# Hero/marketing photo licensing

All photography currently used on the public site is sourced from
[Unsplash](https://unsplash.com) and served directly via Unsplash's image
CDN (`images.unsplash.com`) rather than downloaded and rehosted.

**License**: every photo on Unsplash is published under the
[Unsplash License](https://unsplash.com/license), which permits commercial
use, modification, and use without attribution, with two exceptions we don't
run into here: compiling photos into a competing stock-photo service, and
implying a person or brand shown in a photo endorses BH Group. No image
below shows an identifiable person in a way that could read as an
endorsement.

These are **placeholder/stock imagery**, not photos of actual BH Group
properties — none of the copy near them claims otherwise (see the Phase 1
audit of public-facing copy). Before a real production launch, they should
be replaced with actual photography of BH Group's own managed properties,
at which point this file should be updated to reflect the new source and
licensing (owned/commissioned photography, a model release if people are
shown, etc.).

## Current images in use

| Location | File | Unsplash photo ID |
|---|---|---|
| Homepage hero | `components/marketing/hero-section.tsx` | `photo-1522708323590-d24dbb6b0267` |
| Homepage welcome section | `components/marketing/welcome-section.tsx` | `photo-1502672260266-1c1ef2d93688` |
| Homepage CTA section | `components/marketing/cta-section.tsx` | `photo-1493809842364-78817add7ffb` |
| "Pentru proprietari" hero | `app/pentru-proprietari/page.tsx` | `photo-1560448204-e02f11c3d0e2` |
| Auth pages background (login/mfa/reset password) | `app/(auth)/layout.tsx` | `photo-1512917774080-9991f1c4c750` |

Property photos shown on individual property pages (`/book/[id]`) are a
separate case: those are uploaded by staff per-property through the
dashboard (`PropertyPhoto`), not stock imagery, and licensing for those is
whatever staff agreed to when uploading them - out of scope for this file.
