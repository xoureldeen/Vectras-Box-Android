# Privacy Policy

Effective date: October 10, 2026

This Privacy Policy describes how Vectras LLC ("Vectras," "we," "us," or
"our") handles information in Vectras Box for Android, application ID
`com.xoureldeen.vectrasbox` (the "App").

## Summary

Vectras Box runs PC virtual machines locally. The App does not contain advertising,
analytics, tracking, account creation, or a developer-operated online service.
The App requests Internet and network-state permissions for optional guest
networking. Vectras LLC does not receive your virtual machines, files, or usage
data through a developer-operated service. Software inside a network-enabled
guest can transmit information to the destinations it contacts.

## Information handled on your device

The App creates and uses information needed to provide its emulator features,
including:

- virtual-machine names, identifiers, hardware settings, configuration files,
  NVR data, and machine artwork;
- disk images, ISO images, floppy images, ROM files, fonts, sounds, and other
  emulator assets that you choose to import or create;
- local preferences such as control positions, setup state, and the last active
  virtual machine; and
- information supplied by connected input devices while you use the emulator.

This information is stored in the App's private or app-specific storage. It is
processed locally to configure and run virtual machines. Vectras LLC does not
receive it.

## File access and device features

The App uses Android's system file picker. It can read a file or folder only
after you select it and Android grants access. The App may copy selected files
into its app-specific storage. The App requests vibration access for emulator
feedback. It does not request broad storage, location, contacts, camera,
microphone, or advertising ID permissions. Internet and network-state access
are used for optional NAT networking and reading DNS servers from the active
phone connection when a guest starts.

## Guest networking

Guest networking is disabled by default. When you enable NAT and a virtual
network card, guest traffic uses your phone connection. Remote destinations
and DNS providers may receive your public IP address, requests, and any
information the guest sends. Their own terms and privacy policies apply.
Guest DNS forwarding uses the active network's DNS server addresses and does
not implement Android Private DNS encryption itself. Do not assume that
guest DNS has the same encrypted-DNS guarantees as native Android apps.
Guest software can consume mobile data. Disable networking in the machine
configuration if you want it to remain offline.

## Android backup

Android's automatic backup feature is enabled for the App. Depending on your
device and account settings, Android or your device's backup provider may copy
some App preferences and app-specific files to your personal cloud backup and
restore them later. Vectras LLC does not operate or access that backup. You can
control device backup through Android settings and the applicable provider's
terms and privacy policy.

## Sharing and third parties

The App does not sell personal information and does not send App data to
Vectras LLC, advertisers, analytics providers, or data brokers. Android system
components, your chosen document provider, and your device backup provider may
process information when you use their services.

The App includes links to GitHub and Telegram. Opening a link leaves the App,
and the destination service handles information under its own privacy policy.

## Retention and deletion

App data remains on your device until you delete a machine or file, clear the
App's storage, or uninstall the App. Uninstalling normally removes app-specific
local storage. Copies in device backups, exported files, or locations you chose
outside the App must be deleted through the relevant provider or storage app.

The App has no user accounts and Vectras LLC holds no server-side App data to
delete. For help locating or deleting local data, contact us as described
below.

## Security

The App relies on Android app isolation and scoped file access. Imported ZIP
archives are validated before extraction, and extraction is limited to
app-controlled locations. No method of storage or software is completely
secure, so you should keep backups of important virtual-machine data and obtain
the App only from a source you trust.

## Children

The App is not directed to children under 13, and Vectras LLC does not
knowingly collect children's personal information through the App. A parent or
guardian who believes a child has provided information to Vectras LLC may
contact us.

## Changes to this policy

We may update this policy when the App or legal requirements change. The
effective date above will be updated, and the current version will be published
with the project. Material changes to data handling will also be reflected in
the App and any store disclosures before the changed handling begins.

## Contact

Vectras LLC is the developer and publisher responsible for this policy. Privacy
questions may be submitted through the project's public issue tracker:

<https://github.com/xoureldeen/Vectras-Box-Android/issues>

Do not include private files, disk images, license keys, or other sensitive
information in a public issue.
