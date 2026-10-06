package com.xoureldeen.vectrasbox.data;

import android.content.Context;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.AtomicFile;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class MachineStore {
    private static final long MIB = 1024L * 1024L;
    private static final long RESERVE = 32L * MIB;
    private static final long MAX_MEDIA = 128L * 1024L * MIB;
    private static final long MAX_ROM_FILE = 64L * MIB;
    private static final long MAX_ROM_TOTAL = 1024L * MIB;
    private static final long MAX_ASSET_FILE = 128L * MIB;
    private static final long MAX_ASSET_TOTAL = 2L * 1024L * MIB;
    private static final Set<String> ROM_FOLDERS = new HashSet<>(Arrays.asList(
            "machines", "video", "sound", "network", "hdd", "floppy", "scsi", "other"));
    private static final Set<String> ASSET_FOLDERS = new HashSet<>(Arrays.asList("fonts", "sounds"));
    private static final Set<String> ROM_REQUIRED_FOLDERS = new HashSet<>(Arrays.asList("machines"));
    private static final Set<String> ASSET_REQUIRED_FOLDERS = new HashSet<>(Arrays.asList("fonts", "sounds"));
    private final Context context;
    private final File dataRoot;

    public interface Progress { void onProgress(long copied, long total); }

    public static final class Machine {
        public final String id, name;
        public final File directory, configFile, iconFile;
        public final int ramMb;
        public final String diskPath, isoPath;

        private Machine(String id, String name, File directory, int ramMb,
                        String diskPath, String isoPath) {
            this.id = id;
            this.name = name;
            this.directory = directory;
            this.configFile = new File(directory, "86box.cfg");
            this.iconFile = new File(directory, ".vectras-icon");
            this.ramMb = ramMb;
            this.diskPath = diskPath;
            this.isoPath = isoPath;
        }
    }

    public MachineStore(Context context) {
        this.context = context.getApplicationContext();
        File external = context.getExternalFilesDir(null);
        this.dataRoot = external == null ? context.getFilesDir() : external;
    }

    public File root() { return dataRoot; }
    public File romsDir() { return new File(dataRoot, "roms"); }
    public File assetsDir() { return new File(dataRoot, "assets"); }
    public File imagesDir() { return new File(dataRoot, "images"); }

    public boolean hasRoms() { return containsRequiredRepositoryFiles(romsDir(), ROM_REQUIRED_FOLDERS); }
    public boolean hasAssets() { return containsRequiredRepositoryFiles(assetsDir(), ASSET_REQUIRED_FOLDERS); }
    public boolean isSetupComplete() { return hasRoms() && hasAssets(); }

    public Closeable acquireRuntimeLock() throws IOException {
        mkdir(dataRoot);
        RandomAccessFile handle = new RandomAccessFile(new File(dataRoot, ".runtime.lock"), "rw");
        try {
            FileLock lock = handle.getChannel().tryLock();
            if (lock == null) throw new IOException("Stop the running machine before changing its settings.");
            return () -> {
                try { lock.release(); } finally { handle.close(); }
            };
        } catch (IOException | OverlappingFileLockException error) {
            handle.close();
            throw new IOException("Stop the running machine before changing its settings.", error);
        }
    }

    public boolean isRunning() {
        try (Closeable ignored = acquireRuntimeLock()) { return false; }
        catch (IOException error) { return true; }
    }

    public synchronized List<Machine> list() throws IOException {
        List<Machine> result = new ArrayList<>();
        File[] dirs = new File(dataRoot, "machines").listFiles(File::isDirectory);
        if (dirs == null) return result;
        for (File dir : dirs) {
            if (dir.getName().startsWith(".")) continue;
            if (new File(dir, "86box.cfg").isFile()) result.add(get(dir.getName()));
        }
        result.sort(Comparator.comparing((Machine m) -> !m.id.equals("default"))
                .thenComparing(m -> m.name.toLowerCase(Locale.ROOT)));
        return result;
    }

    public synchronized Machine get(String id) throws IOException {
        File dir = machineDir(id);
        File cfg = new File(dir, "86box.cfg");
        if (!cfg.isFile()) return null;
        Ini ini = new Ini(readText(cfg, 2L * MIB));
        File label = new File(dir, ".vectras-name");
        String fallback = id.equals("default") ? "My Vectras Box PC" : id;
        String name = label.isFile() ? readText(label, 4096).trim() : fallback;
        if (name.isEmpty()) name = fallback;
        int ram = 128;
        try { ram = Integer.parseInt(ini.get("Machine", "mem_size", "131072")) / 1024; }
        catch (NumberFormatException ignored) { }
        return new Machine(id, name, dir, ram,
                absoluteImage(dir, ini.get("Hard disks", "hdd_01_fn", "")),
                absoluteImage(dir, ini.get("Floppy and CD-ROM drives", "cdrom_01_image_path", "")));
    }

    public synchronized Machine create(String name, String configContent) throws IOException {
        try (Closeable ignored = acquireRuntimeLock()) {
            validateName(name);
            String id = "vm-" + UUID.randomUUID().toString();
            File dir = machineDir(id);
            mkdir(dir);
            try {
                atomicWrite(new File(dir, "86box.cfg"), configContent);
                atomicWrite(new File(dir, ".vectras-name"), name.trim());
                return get(id);
            } catch (IOException | RuntimeException error) {
                deleteOwnedTree(dir);
                throw error;
            }
        }
    }

    public synchronized void rename(String id, String name) throws IOException {
        try (Closeable ignored = acquireRuntimeLock()) {
            validateName(name);
            Machine machine = require(id);
            atomicWrite(new File(machine.directory, ".vectras-name"), name.trim());
        }
    }

    public synchronized void setIcon(String id, Uri uri) throws IOException {
        try (Closeable ignored = acquireRuntimeLock()) {
            Machine machine = require(id);
            File temporary = new File(machine.directory, ".vectras-icon.part");
            temporary.delete();
            try (InputStream in = openInput(uri); FileOutputStream out = new FileOutputStream(temporary)) {
                copy(in, out, -1, 10L * MIB, temporary, null);
                out.getFD().sync();
            } catch (IOException | RuntimeException error) {
                temporary.delete();
                throw error;
            }

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(temporary.getAbsolutePath(), bounds);
            if (bounds.outWidth < 1 || bounds.outHeight < 1) {
                temporary.delete();
                throw new IOException("Select a valid PNG, JPG or WEBP image.");
            }
            if (machine.iconFile.exists() && !machine.iconFile.delete()) {
                temporary.delete();
                throw new IOException("Could not replace the machine icon.");
            }
            if (!temporary.renameTo(machine.iconFile)) {
                temporary.delete();
                throw new IOException("Could not save the machine icon.");
            }
        }
    }

    public synchronized void update(String id, int ramMb, String diskPath, String isoPath) throws IOException {
        try (Closeable ignored = acquireRuntimeLock()) {
            validateRam(ramMb);
            Machine machine = require(id);
            Ini ini = new Ini(config(id));
            ini.set("Machine", "mem_size", Integer.toString(ramMb * 1024));
            if (diskPath != null) {
                String path = absoluteImage(machine.directory, diskPath.trim());
                if (path.isEmpty()) {
                    ini.remove("Hard disks", "hdd_01_fn");
                    ini.set("Hard disks", "hdd_01_parameters", "0, 0, 0, 0, none");
                } else {
                    File disk = readableMedia(path, true);
                    if (!sameFile(path, machine.diskPath)) {
                        ini.set("Hard disks", "hdd_01_parameters", geometry(disk) + ", 0, ide");
                        ini.set("Hard disks", "hdd_01_ide_channel", "0:0");
                        ini.set("Hard disks", "hdd_01_speed", "1997_5400rpm");
                    }
                    ini.set("Hard disks", "hdd_01_fn", path);
                }
            }
            if (isoPath != null) {
                String path = absoluteImage(machine.directory, isoPath.trim());
                if (path.isEmpty()) ini.remove("Floppy and CD-ROM drives", "cdrom_01_image_path");
                else {
                    readableMedia(path, false);
                    ini.set("Floppy and CD-ROM drives", "cdrom_01_image_path", path);
                    ini.set("Floppy and CD-ROM drives", "cdrom_01_parameters", "1, atapi");
                    ini.set("Floppy and CD-ROM drives", "cdrom_01_ide_channel", "1:0");
                }
            }
            atomicWrite(machine.configFile, ini.toString());
        }
    }

    public synchronized void prepareLaunch(String id) throws IOException {
        Machine machine = require(id);
        mkdir(romsDir());
        mkdir(imagesDir());
        mkdir(new File(dataRoot, "assets"));
        Ini ini = new Ini(config(id));

        ini.set("Machine", "cpu_use_dynarec", "1");
        String configuredVideo = ini.get("Video", "gfxcard", "").trim();
        String usableVideo = usableVideoCard(configuredVideo);
        if (!usableVideo.equals(configuredVideo)) ini.set("Video", "gfxcard", usableVideo);
        for (String[] entry : ini.entries()) {
            String key = entry[1];
            if (key.matches("hdd_\\d+_fn") || key.matches("(cdrom|fdd|zip|mo)_\\d+_image_path")
                    || key.matches("fdd_\\d+_fn")) {
                if (entry[2].isEmpty()) continue;
                String path = absoluteImage(machine.directory, entry[2]);
                File image = new File(path);
                if (!image.isFile() || !image.canRead())
                    throw new IOException("Image not found: " + path + ". Select it in machine settings.");
                ini.set(entry[0], key, path);
            }
        }
        String updated = ini.toString();
        if (!updated.equals(config(id))) atomicWrite(machine.configFile, updated);
    }

    private static String usableVideoCard(String value) {
        switch (value.toLowerCase(Locale.ROOT)) {
            case "virgedx_pci": return "virge_dx_pci";
            case "virgevx_pci": return "virge_vx_pci";
            case "trio64_pci": return "s3_trio64_pci";
            case "trio3d2x_pci": return "trio3d2x";
            case "voodoo3_2000_pci": return "voodoo3_2k_pci";
            case "et4000_isa": return "et4000ax";
            case "":
            case "none": return "virge_dx_pci";
            default: return value;
        }
    }

    public synchronized String config(String id) throws IOException {
        return readText(require(id).configFile, 2L * MIB);
    }

    public synchronized void saveConfig(String id, String text) throws IOException {
        try (Closeable ignored = acquireRuntimeLock()) {
            if (text == null || text.length() > 2L * MIB || text.indexOf('\0') >= 0
                    || !text.matches("(?s).*\\[Machine\\].*"))
                throw new IOException("A valid PCBox configuration must contain [Machine].");
            atomicWrite(require(id).configFile, text);
        }
    }

    public File createDisk(String name, int sizeGb) throws IOException {
        validateDiskSize(sizeGb);
        mkdir(imagesDir());
        long cylinderBytes = 63L * 16L * 512L;
        long length = (sizeGb * 1024L * MIB / cylinderBytes) * cylinderBytes;
        ensureSpace(imagesDir(), length);
        File file = reserveUnique(imagesDir(), safeName(name, "disk") + ".img");
        try (RandomAccessFile out = new RandomAccessFile(file, "rw")) {
            out.setLength(length);
            out.getFD().sync();
        } catch (IOException error) { file.delete(); throw error; }
        return file;
    }

    public File importMedia(Uri uri, boolean disk, Progress progress) throws IOException {
        MediaInfo info = info(uri);
        if (!validExtension(info.name, disk))
            throw new IOException(disk ? "Select a raw .img, .ima or .raw disk. QCOW2/VHD are not supported."
                    : "Select an .iso installation CD image.");
        mkdir(imagesDir());
        if (info.size > MAX_MEDIA) throw new IOException("The image exceeds 128 GB.");
        if (info.size > 0) ensureSpace(imagesDir(), info.size);
        File temporary = File.createTempFile(".import-", ".part", imagesDir());
        File target = null;
        try {
            try (InputStream in = openInput(uri); FileOutputStream out = new FileOutputStream(temporary)) {
                copy(in, out, info.size, MAX_MEDIA, temporary, progress);
                out.getFD().sync();
            }
            if (temporary.length() == 0) throw new IOException("The selected image is empty.");
            if (disk) geometry(temporary);
            target = reserveUnique(imagesDir(), safeFileName(info.name));
            if (!temporary.renameTo(target)) throw new IOException("Could not finish importing the image.");
            return target;
        } catch (IOException | RuntimeException error) {
            if (target != null) target.delete();
            throw error;
        } finally { temporary.delete(); }
    }

    public int importRomsZip(Uri uri, Progress progress) throws IOException {
        return importRepositoryZip(uri, romsDir(), ROM_FOLDERS, "ROM",
                MAX_ROM_FILE, MAX_ROM_TOTAL, progress);
    }

    public int importAssetsZip(Uri uri, Progress progress) throws IOException {
        return importRepositoryZip(uri, assetsDir(), ASSET_FOLDERS, "assets",
                MAX_ASSET_FILE, MAX_ASSET_TOTAL, progress);
    }

    private int importRepositoryZip(Uri uri, File destination, Set<String> roots, String label,
                                    long maxFile, long maxTotal, Progress progress) throws IOException {
        try (Closeable ignored = acquireRuntimeLock()) {
            MediaInfo info = info(uri);
            ArchivePlan plan = checkRepositoryZip(uri, roots, label, maxFile, maxTotal, info, progress);

            mkdir(destination);
            File stage = new File(dataRoot, "." + label.toLowerCase(Locale.ROOT)
                    + "-import-" + UUID.randomUUID());
            mkdir(stage);
            try {
                extractCheckedRepositoryZip(uri, stage, roots, label, plan, info, progress);
                int imported = 0;
                String stagePrefix = stage.getCanonicalPath() + File.separator;
                String destinationPrefix = destination.getCanonicalPath() + File.separator;
                for (String relative : plan.files.keySet()) {
                    File file = new File(stage, relative);
                    if (!file.isFile()) throw new IOException("Archive changed after validation: " + relative);
                    if (!file.getCanonicalPath().startsWith(stagePrefix))
                        throw new IOException("Unsafe staged path in " + label + " archive.");
                    File target = new File(destination, relative);
                    if (!target.getCanonicalPath().startsWith(destinationPrefix))
                        throw new IOException("Unsafe destination in " + label + " archive.");
                    if (target.exists()) continue;
                    mkdir(target.getParentFile());

                    if (!target.createNewFile()) continue;
                    if (!file.renameTo(target)) {
                        target.delete();
                        throw new IOException("Could not save " + label + " file: " + relative);
                    }
                    imported++;
                }
                return imported;
            } finally {
                deleteOwnedTree(stage);
            }
        }
    }

    private ArchivePlan checkRepositoryZip(Uri uri, Set<String> roots, String label,
                                           long maxFile, long maxTotal, MediaInfo info,
                                           Progress progress) throws IOException {
        try {
            return checkRepositoryZipStream(openInput(uri), roots, label, maxFile, maxTotal,
                    info.size, progress);
        } catch (java.util.zip.ZipException error) {
            throw new IOException("The selected " + label + " ZIP is damaged or incomplete.", error);
        }
    }

    private static ArchivePlan checkRepositoryZipStream(InputStream input, Set<String> roots,
                                                        String label, long maxFile, long maxTotal,
                                                        long archiveSize, Progress progress)
            throws IOException {
        Map<String, ArchiveFile> files = new HashMap<>();
        Map<String, ArchiveFile> aliases = new HashMap<>();
        Map<String, String> seen = new HashMap<>();
        long total = 0;
        int entries = 0;
        byte[] buffer = new byte[64 * 1024];

        try (CountingInputStream counted = new CountingInputStream(input);
             ZipInputStream zip = new ZipInputStream(counted)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 20000)
                    throw new IOException(label + " archive contains too many entries.");
                String relative = repositoryEntryPath(entry.getName(), roots, label);
                if (entry.getSize() > maxFile)
                    throw new IOException("A file in the " + label + " archive is too large.");
                boolean included = relative != null && !entry.isDirectory();
                CRC32 crc = new CRC32();
                long fileSize = 0;
                int count;
                while ((count = zip.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Import cancelled.");
                    fileSize += count;
                    total += count;
                    if (fileSize > maxFile || total > maxTotal
                            || total > counted.count * 500L + 16L * MIB)
                        throw new IOException(label + " archive exceeds safe extraction limits.");
                    if (included) crc.update(buffer, 0, count);
                    reportArchiveProgress(progress, counted.count, archiveSize, false);
                }
                if (included) {
                    ArchiveFile current = new ArchiveFile(fileSize, crc.getValue());
                    String collisionKey = relative.toLowerCase(Locale.ROOT);
                    String previousPath = seen.get(collisionKey);
                    if (previousPath != null) {
                        ArchiveFile previous = files.get(previousPath);
                        boolean identicalCaseAlias = !previousPath.equals(relative)
                                && previous != null
                                && previous.size == current.size
                                && previous.crc == current.crc;
                        if (!identicalCaseAlias)
                            throw new IOException(label + " archive contains conflicting duplicate paths: "
                                    + relative);

                        aliases.put(relative, current);
                        continue;
                    }
                    seen.put(collisionKey, relative);
                    files.put(relative, current);
                }
                zip.closeEntry();
            }
        }
        if (files.isEmpty()) {
            String expected = roots.equals(ROM_FOLDERS) ? "PCBox ROM folders" : "fonts/ or sounds/";
            throw new IOException("No " + expected + " found. Select the official PCBox " + label + " ZIP.");
        }
        Set<String> requiredRoots = roots.equals(ROM_FOLDERS)
                ? ROM_REQUIRED_FOLDERS : ASSET_REQUIRED_FOLDERS;
        for (String root : requiredRoots) {
            boolean found = false;
            String prefix = root + "/";
            for (String path : files.keySet()) {
                if (path.startsWith(prefix)) {
                    found = true;
                    break;
                }
            }
            if (!found)
                throw new IOException("The " + label + " ZIP is missing its required " + root + "/ folder.");
        }
        return new ArchivePlan(files, aliases);
    }

    private static int checkRomArchive(InputStream input) throws IOException {
        return checkRepositoryZipStream(input, ROM_FOLDERS, "ROM", MAX_ROM_FILE,
                MAX_ROM_TOTAL, -1, null).files.size();
    }

    private static int checkAssetsArchive(InputStream input) throws IOException {
        return checkRepositoryZipStream(input, ASSET_FOLDERS, "assets", MAX_ASSET_FILE,
                MAX_ASSET_TOTAL, -1, null).files.size();
    }

    private void extractCheckedRepositoryZip(Uri uri, File stage, Set<String> roots, String label,
                                             ArchivePlan plan, MediaInfo info,
                                             Progress progress) throws IOException {
        Set<String> verified = new HashSet<>();
        byte[] buffer = new byte[64 * 1024];
        try (CountingInputStream counted = new CountingInputStream(openInput(uri));
             ZipInputStream zip = new ZipInputStream(counted)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String relative = repositoryEntryPath(entry.getName(), roots, label);
                ArchiveFile expected = relative == null || entry.isDirectory()
                        ? null : plan.files.get(relative);
                ArchiveFile aliasExpected = relative == null || entry.isDirectory()
                        ? null : plan.aliases.get(relative);
                ArchiveFile checked = expected != null ? expected : aliasExpected;
                if (relative != null && !entry.isDirectory() && checked == null)
                    throw new IOException("Archive changed after validation: " + relative);

                File output = expected == null ? null : new File(stage, relative);
                if (output != null) {
                    String stagePrefix = stage.getCanonicalPath() + File.separator;
                    if (!output.getCanonicalPath().startsWith(stagePrefix))
                        throw new IOException("Unsafe path in " + label + " archive.");
                    mkdir(output.getParentFile());
                }

                CRC32 crc = new CRC32();
                long fileSize = 0;
                try (OutputStream out = output == null
                        ? new DiscardOutputStream() : new FileOutputStream(output)) {
                    int count;
                    while ((count = zip.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) throw new IOException("Import cancelled.");
                        fileSize += count;
                        if (checked != null) {
                            if (fileSize > checked.size)
                                throw new IOException("Archive changed after validation: " + relative);
                            crc.update(buffer, 0, count);
                            if (expected != null) ensureSpace(stage, count);
                        }
                        out.write(buffer, 0, count);
                        reportArchiveProgress(progress, counted.count, info.size, true);
                    }
                }
                if (checked != null) {
                    if (fileSize != checked.size || crc.getValue() != checked.crc
                            || !verified.add(relative))
                        throw new IOException("Archive changed after validation: " + relative);
                }
                zip.closeEntry();
            }
        } catch (java.util.zip.ZipException error) {
            throw new IOException("The selected " + label + " ZIP changed or is damaged.", error);
        }
        if (verified.size() != plan.files.size() + plan.aliases.size())
            throw new IOException("The selected archive changed after validation. Please select it again.");
    }

    private static void reportArchiveProgress(Progress progress, long compressed, long archiveSize,
                                              boolean extracting) {
        if (progress == null) return;
        if (archiveSize > 0) {
            long copied = Math.min(archiveSize, compressed) + (extracting ? archiveSize : 0);
            progress.onProgress(copied, archiveSize * 2L);
        } else {
            progress.onProgress(compressed, -1);
        }
    }

    private static final class ArchivePlan {
        final Map<String, ArchiveFile> files;
        final Map<String, ArchiveFile> aliases;
        ArchivePlan(Map<String, ArchiveFile> files, Map<String, ArchiveFile> aliases) {
            this.files = files;
            this.aliases = aliases;
        }
    }

    private static final class ArchiveFile {
        final long size;
        final long crc;
        ArchiveFile(long size, long crc) { this.size = size; this.crc = crc; }
    }

    public List<File> media(boolean disks) {
        File[] files = imagesDir().listFiles(file -> file.isFile() && validExtension(file.getName(), disks));
        if (files == null) return new ArrayList<>();
        Arrays.sort(files, Comparator.comparing(file -> file.getName().toLowerCase(Locale.ROOT)));
        return new ArrayList<>(Arrays.asList(files));
    }

    public void exportFile(File source, Uri uri, Progress progress) throws IOException {
        if (!source.isFile() || !source.canRead()) throw new IOException("The source file is not readable.");
        try (InputStream in = new FileInputStream(source);
             OutputStream out = context.getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new IOException("The selected destination could not be opened.");
            copy(in, out, source.length(), Long.MAX_VALUE, null, progress);
        }
    }

    private Machine require(String id) throws IOException {
        Machine machine = get(id);
        if (machine == null) throw new IOException("Machine not found.");
        return machine;
    }

    private File machineDir(String id) throws IOException {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,100}")) throw new IOException("Invalid machine ID.");
        return new File(dataRoot, "machines/" + id);
    }

    private static String absoluteImage(File dir, String path) throws IOException {
        path = path.trim();
        if (path.isEmpty()) return "";
        if (path.indexOf('\n') >= 0 || path.indexOf('\r') >= 0 || path.indexOf('\0') >= 0
                || path.startsWith("content:") || path.startsWith("file:"))
            throw new IOException("Import the image into the app before selecting it.");
        File file = new File(path);
        return (file.isAbsolute() ? file : new File(dir, path)).getCanonicalPath();
    }

    private static boolean sameFile(String a, String b) throws IOException {
        return !a.isEmpty() && !b.isEmpty() && new File(a).getCanonicalFile().equals(new File(b).getCanonicalFile());
    }

    private static File readableMedia(String path, boolean disk) throws IOException {
        File file = new File(path);
        if (!file.isFile() || !file.canRead() || file.length() == 0)
            throw new IOException("Image not found or empty: " + file.getName());
        if (!validExtension(file.getName(), disk))
            throw new IOException(disk ? "Only raw IMG/IMA/RAW disks are supported." : "Only ISO CD images are supported.");
        return file;
    }

    private static boolean validExtension(String name, boolean disk) {
        String n = name.toLowerCase(Locale.ROOT);
        return disk ? n.endsWith(".img") || n.endsWith(".ima") || n.endsWith(".raw") : n.endsWith(".iso");
    }

    private static String geometry(File disk) throws IOException {
        long size = disk.length();
        if (size == 0 || size % 512 != 0) throw new IOException("Raw disk size must be a positive multiple of 512 bytes.");
        rejectContainerDisk(disk);
        long sectors = size / 512;

        int[][] preferred = {{63, 16}, {63, 255}, {32, 16}, {63, 32}, {32, 64}, {63, 64}};
        for (int[] pair : preferred) {
            String match = exactGeometry(sectors, pair[0], pair[1]);
            if (match != null) return match;
        }
        for (int heads = 16; heads >= 1; heads--)
            for (int spt = 63; spt >= 1; spt--) {
                String match = exactGeometry(sectors, spt, heads);
                if (match != null) return match;
            }
        for (int heads = 255; heads >= 1; heads--)
            for (int spt = 255; spt >= 1; spt--) {
                String match = exactGeometry(sectors, spt, heads);
                if (match != null) return match;
            }
        throw new IOException("This raw disk size cannot be represented exactly by PCBox IDE geometry. Use a compatible raw image.");
    }

    private static void rejectContainerDisk(File disk) throws IOException {
        try (RandomAccessFile input = new RandomAccessFile(disk, "r")) {
            byte[] header = new byte[8];
            input.readFully(header);
            String signature = new String(header, StandardCharsets.ISO_8859_1);
            boolean container = signature.startsWith("QFI\u00fb") || signature.startsWith("vhdxfile")
                    || signature.startsWith("KDMV") || signature.startsWith("COWD")
                    || signature.startsWith("conectix");
            if (input.length() >= 512) {
                input.seek(input.length() - 512);
                input.readFully(header);
                container |= new String(header, StandardCharsets.ISO_8859_1).equals("conectix");
            }
            if (container) throw new IOException("This is a container disk, not raw IMG. Convert QCOW2/VHD/VMDK to raw first.");
        }
    }

    private static String exactGeometry(long sectors, int spt, int heads) {
        long perCylinder = (long) spt * heads;
        long cylinders = sectors / perCylinder;
        if (sectors % perCylinder != 0 || cylinders < 1 || cylinders > 266305) return null;
        return spt + ", " + heads + ", " + cylinders;
    }

    private static void validateName(String name) throws IOException {
        if (name == null || name.trim().isEmpty() || name.trim().length() > 80
                || name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0 || name.indexOf('\0') >= 0)
            throw new IOException("Enter a machine name between 1 and 80 characters.");
    }

    private static void validateRam(int ram) throws IOException {
        if (ram < 4 || ram > 2048) throw new IOException("Select between 4 and 2048 MB of RAM.");
    }

    private static void validateDiskSize(int sizeGb) throws IOException {
        if (sizeGb != 1 && sizeGb != 2 && sizeGb != 4 && sizeGb != 8)
            throw new IOException("Select a 1, 2, 4 or 8 GB disk.");
    }

    private static String safeName(String name, String fallback) {
        String clean = name.replaceAll("[^\\p{L}\\p{N}._ -]", "_").trim();
        if (clean.startsWith(".")) clean = "media-" + clean;
        if (clean.length() > 100) clean = clean.substring(0, 100);
        return clean.isEmpty() ? fallback : clean;
    }

    private static String safeFileName(String name) {
        int dot = name.lastIndexOf('.');
        return safeName(dot < 0 ? name : name.substring(0, dot), "image")
                + (dot < 0 ? "" : name.substring(dot).toLowerCase(Locale.ROOT));
    }

    private static File reserveUnique(File dir, String name) throws IOException {
        int dot = name.lastIndexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        String ext = dot < 0 ? "" : name.substring(dot);
        for (int i = 0; i < 10000; i++) {
            File candidate = new File(dir, base + (i == 0 ? "" : "-" + i) + ext);
            if (candidate.createNewFile()) return candidate;
        }
        throw new IOException("Too many images with the same name.");
    }

    private static void mkdir(File dir) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create folder: " + dir.getName());
    }

    private static void ensureSpace(File file, long bytes) throws IOException {
        File directory = file.isDirectory() ? file : file.getParentFile();
        if (directory.getUsableSpace() < bytes + RESERVE)
            throw new IOException("Not enough phone storage. Free space and try again.");
    }

    private static void copy(InputStream in, OutputStream out, long total, long limit, File target, Progress progress) throws IOException {
        byte[] buffer = new byte[256 * 1024];
        long copied = 0, nextSpaceCheck = 0;
        int count;
        while ((count = in.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Copy cancelled.");
            copied += count;
            if (copied > limit) throw new IOException("Image exceeds the supported size limit.");
            if (target != null && copied >= nextSpaceCheck) {
                ensureSpace(target, count);
                nextSpaceCheck = copied + 8L * MIB;
            }
            out.write(buffer, 0, count);
            if (progress != null) progress.onProgress(copied, total);
        }
        if (total >= 0 && copied != total) throw new IOException("The selected file changed or its transfer was incomplete.");
        out.flush();
    }

    private InputStream openInput(Uri uri) throws IOException {
        InputStream in = context.getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("Could not open the selected file.");
        return in;
    }

    private MediaInfo info(Uri uri) {
        String name = uri.getLastPathSegment() == null ? "image" : uri.getLastPathSegment();
        long size = -1;
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int n = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int s = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (n >= 0 && !cursor.isNull(n)) name = cursor.getString(n);
                if (s >= 0 && !cursor.isNull(s)) size = cursor.getLong(s);
            }
        } catch (RuntimeException ignored) { }
        return new MediaInfo(name, size);
    }

    private static final class MediaInfo {
        final String name;
        final long size;
        MediaInfo(String name, long size) { this.name = name; this.size = size; }
    }

    private static String romEntryPath(String name) throws IOException {
        return repositoryEntryPath(name, ROM_FOLDERS, "ROM");
    }

    private static String assetEntryPath(String name) throws IOException {
        return repositoryEntryPath(name, ASSET_FOLDERS, "assets");
    }

    private static String repositoryEntryPath(String name, Set<String> roots,
                                              String label) throws IOException {
        String normalized = name.replace('\\', '/');
        if (normalized.length() > 2048 || normalized.startsWith("/") || normalized.indexOf(':') >= 0
                || normalized.indexOf('\0') >= 0)
            throw new IOException("Unsafe path in " + label + " archive.");
        String[] components = normalized.split("/");
        int start = -1;
        for (int i = 0; i < components.length; i++) {
            if (components[i].equals("..") || components[i].equals("."))
                throw new IOException("Unsafe path in " + label + " archive.");
            if (start < 0 && roots.contains(components[i])) start = i;
        }
        if (start < 0 || start == components.length - 1) return null;
        return String.join("/", Arrays.copyOfRange(components, start, components.length));
    }

    private static boolean containsRequiredRepositoryFiles(File directory, Set<String> roots) {
        for (String root : roots) {
            if (!containsFile(new File(directory, root))) return false;
        }
        return true;
    }

    private static boolean containsFile(File directory) {
        File[] children = directory.listFiles();
        if (children == null) return false;
        for (File child : children) {
            if (child.isFile()) return true;
            if (child.isDirectory() && containsFile(child)) return true;
        }
        return false;
    }

    private static void deleteOwnedTree(File directory) {
        File[] children = directory.listFiles();
        if (children != null) for (File child : children) {
            if (child.isDirectory()) deleteOwnedTree(child); else child.delete();
        }
        directory.delete();
    }

    private static String readText(File file, long limit) throws IOException {
        try (InputStream in = new AtomicFile(file).openRead()) { return readText(in, limit); }
    }

    private static String readText(InputStream in, long limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = in.read(buffer)) != -1) {
            if ((long) out.size() + count > limit) throw new IOException("Configuration file is too large.");
            out.write(buffer, 0, count);
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private static void atomicWrite(File file, String text) throws IOException {
        AtomicFile atomic = new AtomicFile(file);
        FileOutputStream out = null;
        try {
            out = atomic.startWrite();
            out.write(text.getBytes(StandardCharsets.UTF_8));
            atomic.finishWrite(out);
        } catch (IOException | RuntimeException error) {
            if (out != null) atomic.failWrite(out);
            throw error;
        }
    }

    private static final class CountingInputStream extends FilterInputStream {
        long count;
        CountingInputStream(InputStream in) { super(in); }
        @Override public int read() throws IOException { int value = in.read(); if (value >= 0) count++; return value; }
        @Override public int read(byte[] b, int off, int len) throws IOException {
            int n = in.read(b, off, len); if (n > 0) count += n; return n;
        }
    }

    private static final class DiscardOutputStream extends OutputStream {
        @Override public void write(int value) { }
        @Override public void write(byte[] b, int offset, int length) { }
    }

    private static final class Ini {
        private final List<String> lines;
        Ini(String text) { lines = new ArrayList<>(Arrays.asList(text.replace("\r\n", "\n").split("\n", -1))); }

        String get(String section, String key, String fallback) {
            String result = fallback;
            for (String[] entry : entries()) if (entry[0].equals(section) && entry[1].equals(key)) result = entry[2];
            return result;
        }

        List<String[]> entries() {
            List<String[]> result = new ArrayList<>();
            String section = "";
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) section = trimmed.substring(1, trimmed.length() - 1);
                else if (!trimmed.startsWith(";") && !trimmed.startsWith("#")) {
                    int equals = trimmed.indexOf('=');
                    if (equals > 0) result.add(new String[]{section, trimmed.substring(0, equals).trim(), trimmed.substring(equals + 1).trim()});
                }
            }
            return result;
        }

        void set(String section, String key, String value) {
            remove(section, key);
            int insertion = -1;
            boolean inSection = false;
            for (int i = 0; i < lines.size(); i++) {
                String trimmed = lines.get(i).trim();
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    if (inSection) break;
                    inSection = trimmed.equals("[" + section + "]");
                }
                if (inSection) insertion = i + 1;
            }
            if (insertion < 0) {
                lines.add("");
                lines.add("[" + section + "]");
                insertion = lines.size();
            }
            lines.add(insertion, key + " = " + value);
        }

        void remove(String section, String key) {
            String current = "";
            for (int i = 0; i < lines.size(); i++) {
                String trimmed = lines.get(i).trim();
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) current = trimmed.substring(1, trimmed.length() - 1);
                else if (current.equals(section) && !trimmed.startsWith(";") && !trimmed.startsWith("#")) {
                    int equals = trimmed.indexOf('=');
                    if (equals > 0 && trimmed.substring(0, equals).trim().equals(key)) lines.remove(i--);
                }
            }
        }

        @Override public String toString() { return String.join("\n", lines); }
    }
}
