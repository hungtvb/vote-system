# Original Stitch export manifest

This manifest records the approved Google Stitch ZIP reviewed for the Ballot Edition public MVP.

## Source

- Stitch project: `https://stitch.withgoogle.com/projects/8503802528998412584`
- Original archive name: `stitch_ballot_edition_voting_system.zip`
- Archive SHA-256: `7a4a71d20ff4835b015cace163caecefc07b836a41f5bbf127a5c1f8cd0ac3d3`

## Exported files

| Screen/artifact | Original file | SHA-256 |
|---|---|---|
| Stitch design notes | `ballot_edition/DESIGN.md` | `c19e4d22ea10a89e30336451c818a407151c2424e858b14438704c91e77e6d8e` |
| Feed markup | `feed_vote_system/code.html` | `f2454d3572f2dace91bdd54cd3f4f9877e507baaa3ad1a14d16c03f2d9c59b1a` |
| Feed preview | `feed_vote_system/screen.png` | `32d8bf7286c03547e17d93531bb784b0b365a1c864fb27788e53cbe91739e131` |
| Post detail markup | `post_detail_vote_system/code.html` | `ad35984b38a64f6a8f2620b8f081156b351d4b79e080806985792c65b81c8c21` |
| Post detail preview | `post_detail_vote_system/screen.png` | `870b0d1b532d77ced2d53dedd168e459833a1686be14301cc5bbed74f993e79e` |
| Create post markup | `create_post_vote_system/code.html` | `22ff776bd3610c1d011b244f078f336fac9ca3a53658d9bf6f6c6039ac9f10d2` |
| Create post preview | `create_post_vote_system/screen.png` | `6990fcc94cfa7c4b225facde91b724ce9b9ca4e846419220014f22e5be07ada0` |
| Login/register markup | `login_register_vote_system/code.html` | `fde3e30adb1355dea8266817bf8bd9027bfd0d1bf13232aee652cfebf695f6f1` |
| Login/register preview | `login_register_vote_system/screen.png` | `a2dfdaf1d87228eb6335ce1b52d82b926a92e95abe5cd780c4df2a91ff583700` |
| Stamped logo preview | `vote_system_stamped_logo/screen.png` | `d2f2b81940cd3a33bfcac57c001bebb80a4482bde8185635b1649ef09bf62995` |

## Repository policy

- Root `DESIGN.md` is the product and implementation source of truth.
- `stitch-design.md` preserves the approved visual language.
- `screens.md` preserves screen-level decisions and review notes.
- Generated HTML is reference-only and must not be copied directly into production components.
- Production UI must be rebuilt in Next.js, TypeScript, and SCSS Modules.
- The checksums above allow future exports or downloaded artifacts to be compared against the approved source.

## Visual QA

Use the PNG previews from the original archive for pixel-level comparison at the exported desktop size. Responsive behavior at `375px` and `768px` is governed by root `DESIGN.md`, because the Stitch export only supplied desktop previews.