# 5. Website and Google

The website is plain HTML in the `docs/` folder. GitHub Pages publishes it automatically 1–2 minutes after you push.

- Site: https://winthiha.github.io/singcomfy/
- Privacy policy: https://winthiha.github.io/singcomfy/privacy.html
- Pages settings: https://github.com/WinThiha/singcomfy/settings/pages (source: branch `main`, folder `/docs`)

## Files in `docs/`

| File | What it is |
|---|---|
| `index.html` | The home page, including the hidden search-engine info at the top |
| `privacy.html` | Privacy policy. Update it if the app starts using new permissions or internet services |
| `screenshots/home.png`, `live.png`, `panel.png` | Phone screenshots shown on the site (540×1200) |
| `og.png` | The 1200×630 preview image shown when the link is shared on social media or chat apps |
| `sitemap.xml` | List of pages for search engines |
| `googlebeee7b6461b76bfe.html` | Google ownership proof. **Never delete** |
| `7b0b0c673d25cf75004393bf4da46783.txt` | Bing/IndexNow key. **Never delete** |

## Edit the site

1. Edit the file. GitHub's website works too: open the file → pencil icon.
2. Preview locally by double-clicking `docs/index.html`.
3. When you change a page, update its `<lastmod>` date in `sitemap.xml` (format `2026-11-08`).
4. Commit and push. Check the live site after 1–2 minutes. Press Ctrl+F5 if you still see the old version.

### Text that matters for Google

At the top of `index.html`:
- `<title>`: the blue link text in Google results.
- `<meta name="description">`: the grey text under it.
- The two `<script type="application/ld+json">` blocks: app info and the FAQ, which Google can show as rich results. If you change a FAQ answer on the page, change it in the FAQ block too, so they match.

Keep the words people actually search for in the title, description and headings: *karaoke*, *key changer*, *pitch*, *YouTube*, *Android*, *vocal remover*.

## New screenshots

1. On the phone, take screenshots: power + volume down.
2. Copy them to the PC, resize to 540×1200, and replace the files in `docs/screenshots/` with the same names.
   - With ffmpeg: `ffmpeg -i big.png -vf scale=540:1200 docs\screenshots\home.png`
   - Without ffmpeg: Windows Photos → Resize, or any online resizer.

From a USB-connected phone, `adb exec-out screencap -p > shot.png` saves a screenshot straight to the PC.

## Rebuild the preview image (`og.png`)

Run this in **Git Bash** from the `docs` folder after updating the screenshots:

```bash
ffmpeg -y -f lavfi -i "gradients=s=1200x630:c0=0x2a0f3a:c1=0x0e0b16:x0=0:y0=0:x1=1200:y1=630:nb_colors=2,format=rgb24" \
 -i screenshots/home.png -i screenshots/panel.png \
 -filter_complex "[1]scale=-1:560,format=rgba[a];[2]scale=-1:560,format=rgba[b];[0][a]overlay=x=660:y=35[t1];[t1][b]overlay=x=920:y=35[t2];\
[t2]drawtext=fontfile='C\:/Windows/Fonts/segoeuib.ttf':text='SingComfy':fontcolor=white:fontsize=104:x=68:y=120,\
drawtext=fontfile='C\:/Windows/Fonts/segoeui.ttf':text='Every song, in your key.':fontcolor=0xd8cfe8:fontsize=38:x=74:y=262,\
drawbox=x=78:y=338:w=120:h=8:color=0xff3d8b@1:t=fill,\
drawtext=fontfile='C\:/Windows/Fonts/segoeuib.ttf':text='Too high? Too low?':fontcolor=0xff3d8b:fontsize=46:x=74:y=375,\
drawtext=fontfile='C\:/Windows/Fonts/segoeuib.ttf':text='Make it fit your voice.':fontcolor=0xff3d8b:fontsize=46:x=74:y=432,\
drawtext=fontfile='C\:/Windows/Fonts/segoeui.ttf':text='Karaoke key changer for Android · Free':fontcolor=0xb7aecb:fontsize=28:x=74:y=520" \
 -frames:v 1 og.png
```

Or make a 1200×630 image in Canva and save it as `docs/og.png`.

## Google Search Console

https://search.google.com/search-console, signed in with your Google account. Property: `https://winthiha.github.io/singcomfy/`.

| Task | Where |
|---|---|
| See searches, clicks and your position | **Performance** |
| Check whether Google has a page | Paste the URL in the search bar at the top (**URL inspection**) |
| Ask Google to re-read a changed page | URL inspection → **Request indexing**. Do it once per change, repeating doesn't help |
| Sitemap status | **Sitemaps**. "Couldn't fetch" right after submitting is normal and clears within a few days |
| Problems | **Pages** (why pages aren't indexed) and the bell icon |

Data appears 2–3 days after a page gets indexed.

## Tell Bing, DuckDuckGo and Yahoo about changes

They all use IndexNow. After a site change, run this in Git Bash:

```bash
curl -X POST https://api.indexnow.org/indexnow -H "Content-Type: application/json; charset=utf-8" -d '{"host":"winthiha.github.io","key":"7b0b0c673d25cf75004393bf4da46783","keyLocation":"https://winthiha.github.io/singcomfy/7b0b0c673d25cf75004393bf4da46783.txt","urlList":["https://winthiha.github.io/singcomfy/","https://winthiha.github.io/singcomfy/privacy.html"]}'
```

`202` or `200` means accepted. Bing Webmaster Tools (https://www.bing.com/webmasters) can also import the site from Google Search Console in one click.

## Ranking higher

Google ranks pages higher when other sites link to them. What helps most:
- Posts where karaoke and Android people hang out: r/karaoke, r/singing, r/androidapps, XDA Forums. Show what it does and link the site.
- App lists: AlternativeTo, IzzyOnDroid (an F-Droid-style repo), "awesome Android apps" lists on GitHub.
- A short YouTube or TikTok demo: sing a song before and after changing the key, and put the link in the description.
- Answer people asking "how do I change the key of a YouTube song" on Reddit or Quora.

Repeating keywords on your own page doesn't help much. Links and real users do.

## A custom domain (optional)

A domain like `singcomfy.app` looks more trustworthy and survives a repo rename. Buy one (Cloudflare, Namecheap, about $10–15/year), then on GitHub: **Settings → Pages → Custom domain**. Follow GitHub's DNS instructions, then add the new address as a new property in Search Console.
