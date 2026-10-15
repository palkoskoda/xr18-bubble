# Apple preview privacy notes

XR18 Bubble for iPhone and iPad is developed by Pavol Škoda. Contact: [pavolskoda@azet.sk](mailto:pavolskoda@azet.sk).

This describes the native development preview in this directory. It is not an App Store privacy declaration or an approved release.

## Data on your device

The app stores mixer profile names, IP addresses or hostnames, Wi-Fi names, selected controls and appearance preferences in local app storage. Wi-Fi passwords entered in the app are stored in the device’s Keychain with access limited to this app while the device is unlocked; they do not synchronize through iCloud Keychain.

The app has no developer-operated server, account system, advertising, analytics SDK or crash-reporting service. It does not send profiles or passwords to the developer. iOS uses the Wi-Fi credentials you provide to configure a network connection.

## Permissions and mixer traffic

- **Local Network:** sends OSC/UDP requests directly to the mixer address you choose and receives mixer state and meter readings.
- **Wi-Fi configuration:** asks iOS to join a network saved in your mixer or return profile. iOS controls approval and association.
- **Location permission, when joining mixer Wi-Fi:** allows the app to read the current Wi-Fi name so it can identify a possible return network. The app does not request geographic coordinates, start location tracking, store your position or share location information.

The mixer protocol does not encrypt or authenticate its control messages. Use a mixer network you trust. An explicit offline Demo does not communicate with a mixer or change Wi-Fi.

## Removing saved information

Deleting a mixer profile removes its local profile and the corresponding passwords stored by this app in Keychain. This does not forget a Wi-Fi network saved by iOS. Manage or forget those networks separately in the system Wi-Fi settings.

Before a public Apple release, these notes must be checked against the final app, linked from a publicly accessible policy page, and matched to App Store Connect’s privacy answers.

Slovensky: Profily a nastavenia ostávajú v zariadení, heslá v Keychain. Aplikácia neposiela tieto údaje vývojárovi, nemá analytiku ani reklamy. Povolenie polohy slúži na zistenie názvu Wi-Fi; aplikácia nezisťuje zemepisnú polohu. Zmazanie profilu odstráni údaje uložené aplikáciou, nie Wi-Fi sieť uloženú v Nastaveniach iOS.
