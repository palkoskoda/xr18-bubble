# XR18 Bubble website

Static English and Slovak pages with local app screenshots. No runtime JavaScript, cookies, analytics, external fonts or build step.

- English home: index.html
- Slovak home: sk/index.html
- English privacy policy (stable public URL): privacy.html
- Full Slovak privacy policy: sk/privacy.html
- Shared styles: styles.css
- Original app screenshots and sharing artwork: assets/

The visible EN/SK switch links to the matching page in the other language. Locale-specific gallery images use actual English and Slovak app screens. The hero presents the original demo-panel screenshot through a CSS viewport; the source PNG is unchanged. Demo levels do not represent additional hardware testing.

Publish the entire site directory, including sk/, assets/ and .nojekyll, to the root of gh-pages. Android source remains on main. The stylesheet query version prevents previously cached CSS from styling a new page incorrectly; increment it when publishing a layout update.

Public site: https://palkoskoda.github.io/xr18-bubble/
Slovak: https://palkoskoda.github.io/xr18-bubble/sk/
Author and support: Pavol Škoda / pavolskoda@azet.sk.
