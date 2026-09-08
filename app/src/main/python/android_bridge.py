"""Android adapter for the original DLXhub Python engines.

The files under ``core/``, ``modules/`` and ``engines/`` are copied from the
Termux CLI project unchanged.  This module is deliberately only an Android
entrypoint: it delegates extraction, format selection, downloads, cookies,
retry-capable yt-dlp configuration and RedGIFs HTTP handling to those engines.
The only Android-specific primitive it requests is FFmpeg execution through
``ffmpeg_runner`` because Chaquopy does not ship an Android ffmpeg executable.
"""

import json
import re
from pathlib import Path
from urllib.parse import urlparse

import yt_dlp


def _clean_url(value):
    if not value:
        return ""
    return str(value).replace("\\/", "/").replace("\\u0026", "&").replace("&amp;", "&")


def _platform_headers(platform):
    from engines import fbX_dl, XF_dl
    if platform == "facebook":
        return dict(fbX_dl.HEADERS)
    if platform == "twitter":
        return dict(fbX_dl.HEADERS)
    if platform == "xxxfollow":
        return dict(XF_dl.HEADERS)
    if platform == "youtube":
        return {"User-Agent": "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36"}
    return {}


def _probe_options(url, platform, cookie_path=None, playlist=False):
    opts = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "ignoreerrors": False,
        "retries": 5,
        "fragment_retries": 5,
        "socket_timeout": 30,
        "http_headers": _platform_headers(platform),
        "noplaylist": not playlist,
        "extract_flat": bool(playlist),
    }
    if cookie_path and Path(cookie_path).exists():
        opts["cookiefile"] = cookie_path
    return opts


def _sanitize_info(info, format_info=None):
    if not info:
        raise RuntimeError("Extractor returned no media information")

    entries = []
    for entry in info.get("entries") or []:
        if not entry:
            continue
        vid = entry.get("id") or ""
        webpage = entry.get("webpage_url") or entry.get("original_url") or entry.get("url") or ""
        if vid and not webpage.startswith("http") and "youtube" in (info.get("extractor") or "").lower():
            webpage = f"https://www.youtube.com/watch?v={vid}"
        if webpage:
            entries.append({
                "index": len(entries) + 1,
                "id": vid,
                "title": entry.get("title") or vid or "Unknown title",
                "url": webpage,
                "duration": entry.get("duration"),
            })

    # Keep the actual format metadata available to the Android selector.  A
    # format is not required to expose a direct URL during probe time.
    format_source = format_info or info
    raw_formats = [f for f in (format_source.get("formats") or []) if isinstance(f, dict)]
    if not raw_formats and format_source.get("url"):
        raw_formats = [format_source]

    video_heights = sorted({
        int(f.get("height")) for f in raw_formats
        if f.get("vcodec") not in (None, "none") and f.get("height")
    }, reverse=True)
    audio_bitrates = sorted({
        int(round(float(f.get("abr")))) for f in raw_formats
        if f.get("acodec") not in (None, "none") and f.get("abr")
    }, reverse=True)

    video_qualities = [{"id": "best", "label": "Best available", "value": "Best available", "format": "AUTO", "best": True}]
    video_qualities += [
        {"id": str(h), "label": f"{h}p", "value": f"{h}p", "format": "AUTO", "best": False}
        for h in video_heights
    ]
    audio_qualities = [{"id": "best", "label": "Best available", "value": "Best available", "format": "AUTO", "best": True}]
    audio_qualities += [
        {"id": str(a), "label": f"{a} kbps", "value": f"{a} kbps", "format": "SOURCE", "best": False}
        for a in audio_bitrates
    ]

    return {
        "id": info.get("id") or "",
        "title": info.get("title") or info.get("fulltitle") or "Unknown title",
        "uploader": info.get("channel") or info.get("uploader") or info.get("uploader_id") or "",
        "duration": info.get("duration"),
        "thumbnail": info.get("thumbnail") or "",
        "webpage_url": info.get("webpage_url") or "",
        "extractor": info.get("extractor") or "",
        "is_playlist": info.get("_type") == "playlist" or bool(info.get("entries")),
        "playlist_title": info.get("title") if info.get("_type") == "playlist" else None,
        "entries": entries,
        "video_qualities": video_qualities,
        "audio_qualities": audio_qualities,
    }


def _redgifs_info(url):
    from engines.RG_dl import RedGifsClient
    client = RedGifsClient()
    candidates = client.resolve_candidates(url)
    if not candidates:
        raise RuntimeError("RedGIFs returned no downloadable media")
    first = candidates[0]
    qualities = []
    for candidate in candidates:
        q = str(candidate.get("quality") or "SD").upper()
        qualities.append({
            "id": q.lower(),
            "label": q,
            "value": q,
            "format": "WEBM" if str(candidate.get("url") or "").lower().split("?")[0].endswith(".webm") else "MP4",
            "best": q == "HD" or (q != "GIF" and not any(x.get("id") == "hd" for x in qualities)),
        })
    return {
        "id": first.get("id") or "",
        "title": first.get("title") or first.get("id") or "RedGIFs media",
        "uploader": "RedGIFs",
        "duration": first.get("duration"),
        "thumbnail": first.get("thumbnail") or "",
        "webpage_url": url,
        "extractor": "redgifs:api",
        "is_playlist": False,
        "playlist_title": None,
        "entries": [],
        "video_qualities": qualities,
        # RedGIFs CLI downloads the source video first and extracts MP3 from
        # that same real file. Expose the same best-source audio action in the
        # Android selector; FFmpeg will reject it truthfully if the source has
        # no audio track.
        "audio_qualities": [{
            "id": "best", "label": "Best available", "value": "Best available",
            "format": "MP3", "best": True
        }],
    }


def _youtube_playlist_info(url):
    """Use the original CLI playlist resolver verbatim for playlist mode."""
    from engines import YT_dl as eng

    name, entries = eng.get_playlist_metadata(url)
    normalized_entries = []
    for entry in entries:
        entry = dict(entry)
        entry_url = entry.get("webpage_url") or entry.get("original_url") or entry.get("url")
        entry_id = entry.get("id") or ""
        if entry_url and not str(entry_url).startswith("http") and entry_id:
            entry_url = f"https://www.youtube.com/watch?v={entry_id}"
        if not entry_url:
            continue
        entry["webpage_url"] = entry_url
        entry["url"] = entry_url
        normalized_entries.append(entry)

    if not normalized_entries:
        raise RuntimeError("No downloadable videos were found in this playlist.")

    return {
        "_type": "playlist",
        "id": "",
        "title": name,
        "webpage_url": url,
        "extractor": "youtube:playlist",
        "entries": normalized_entries,
    }


def extract_media(url, platform, cookie_path=None, playlist=False):
    platform = (platform or "").lower()
    url = str(url).strip()
    if not url:
        raise ValueError("URL is empty")
    if platform == "redgifs":
        if playlist:
            raise RuntimeError("RedGIFs does not expose a supported playlist/collection URL through its original CLI engine.")
        return json.dumps(_redgifs_info(url), separators=(",", ":"))

    normalized = re.sub(
        r"^https?://(?:www\.)?x\.com",
        "https://twitter.com",
        url,
        flags=re.I,
    ) if platform == "twitter" else url

    # Dedicated playlist mode deliberately follows the original CLI for
    # YouTube. This avoids Android-specific YouTube headers and keeps playlist
    # discovery, naming and entry resolution identical to the CLI.
    if platform == "youtube" and playlist:
        info = _youtube_playlist_info(normalized)
        format_info = None
        first = next((e for e in info.get("entries") or [] if e), None)
        first_url = first.get("webpage_url") if first else None
        if first_url:
            from engines import YT_dl as eng
            format_info = eng.extract_info(first_url, flat=False)
        return json.dumps(_sanitize_info(info, format_info), separators=(",", ":"))

    # Preserve the existing single-media probe behavior exactly. In particular,
    # YouTube's established single-media path keeps its existing probe mode.
    probe_playlist = False if platform != "youtube" else True
    with yt_dlp.YoutubeDL(_probe_options(normalized, platform, cookie_path, probe_playlist)) as ydl:
        info = ydl.extract_info(normalized, download=False)

    format_info = None
    if platform == "youtube" and info and (info.get("_type") == "playlist" or info.get("entries")):
        entries = [e for e in (info.get("entries") or []) if e]
        if entries:
            first = entries[0]
            first_url = first.get("webpage_url") or first.get("original_url") or first.get("url")
            first_id = first.get("id") or ""
            if first_url and not str(first_url).startswith("http") and first_id:
                first_url = f"https://www.youtube.com/watch?v={first_id}"
            if first_url:
                try:
                    with yt_dlp.YoutubeDL(_probe_options(str(first_url), platform, cookie_path, False)) as item_ydl:
                        format_info = item_ydl.extract_info(str(first_url), download=False)
                except Exception:
                    format_info = None

    return json.dumps(_sanitize_info(info, format_info), separators=(",", ":"))


def _progress_hook(callback, start_percent=0.0, span_percent=100.0):
    def hook(data):
        if callback is None:
            return
        status = data.get("status") or ""
        downloaded = int(data.get("downloaded_bytes") or 0)
        total = int(data.get("total_bytes") or data.get("total_bytes_estimate") or 0)
        speed = int(data.get("speed") or 0)
        eta = data.get("eta")
        try:
            eta = int(eta) if eta is not None else None
        except (TypeError, ValueError):
            eta = None
        ratio = (downloaded / total) if total else 0.0
        pct = start_percent + min(1.0, ratio) * span_percent
        text = "Downloading..." if status == "downloading" else ("Processing..." if status == "finished" else status)
        callback.onProgress(downloaded, total, speed, eta, pct, text)
    return hook


def _find_output(directory, before):
    current = {p for p in Path(directory).rglob("*") if p.is_file()}
    candidates = [
        p for p in current - before
        if p.suffix.lower() not in {".part", ".ytdl", ".temp"} and not p.name.endswith(".part")
    ]
    candidates.sort(key=lambda p: p.stat().st_mtime, reverse=True)
    return candidates[0] if candidates else None


def _engine_ytdlp_options(platform, url, selector, outdir, mode, cookie_path, callback, start, span):
    if platform == "youtube":
        from engines import YT_dl as eng
        opts = eng.video_options(outdir, None, selector) if mode == "video" else eng.audio_options(outdir, None, selector)
    elif platform in ("facebook", "twitter"):
        from engines import fbX_dl as eng
        opts = eng.get_ytdlp_options(url, selector, outdir, mode)
    elif platform == "xxxfollow":
        from engines import XF_dl as eng
        opts = eng.ytdlp_opts(selector, outdir, mode)
    else:
        raise RuntimeError(f"Unsupported yt-dlp engine platform: {platform}")

    # The original engine options are retained. Android only removes the
    # terminal FFmpeg postprocessor because the actual native FFmpeg runtime
    # is invoked explicitly by Python through ffmpeg_runner below.
    opts.pop("postprocessors", None)
    opts.pop("merge_output_format", None)
    opts["noplaylist"] = True
    opts["quiet"] = True
    opts["no_warnings"] = True
    opts["noprogress"] = True
    opts["progress_hooks"] = [_progress_hook(callback, start, span)]
    opts["outtmpl"] = str(Path(outdir) / "%(title).180B [%(id)s].%(ext)s")
    if cookie_path and Path(cookie_path).exists():
        opts["cookiefile"] = cookie_path
    return opts


def _download_component(platform, url, selector, outdir, mode, cookie_path, callback, start, span):
    outdir = Path(outdir)
    outdir.mkdir(parents=True, exist_ok=True)
    before = {p for p in outdir.rglob("*") if p.is_file()}
    opts = _engine_ytdlp_options(platform, url, selector, outdir, mode, cookie_path, callback, start, span)
    with yt_dlp.YoutubeDL(opts) as ydl:
        info = ydl.extract_info(url, download=False)
        if not info:
            raise RuntimeError("No downloadable media information returned")
        ydl.download([url])
    output = _find_output(outdir, before)
    if not output or output.stat().st_size <= 0:
        raise RuntimeError("Download completed but output file could not be resolved")
    return output, info


def _selector_for_choice(platform, mode, choice):
    choice = str(choice or "best")
    # XXXFollow's CLI engine intentionally uses bestvideo* for its best-video
    # selector. The generic selector used by other engines is different and
    # caused the Android build to ask yt-dlp for an unavailable format.
    if platform == "xxxfollow":
        from engines import XF_dl
        if choice == "best":
            return XF_dl.ytdlp_opts(None, None, mode).get("format") or (
                "bestvideo*+bestaudio/best" if mode == "video" else "bestaudio/best"
            )
    from core.quality import selector
    return selector(mode, choice)


def _format_probe(url, platform, cookie_path):
    normalized = re.sub(
        r"^https?://(?:www\.)?x\.com",
        "https://twitter.com",
        url,
        flags=re.I,
    ) if platform == "twitter" else url
    with yt_dlp.YoutubeDL(_probe_options(normalized, platform, cookie_path, False)) as ydl:
        return normalized, ydl.extract_info(normalized, download=False)


def _chosen_format(info, selector):
    first = selector.split("+")[0].split("/")[0].strip()
    for fmt in info.get("formats") or []:
        if str(fmt.get("format_id")) == first:
            return fmt
    return None


def _download_facebook_direct(url, outdir, cookie_path, callback):
    """Use the original Facebook direct extractor, but keep file writing here
    only as the Android output adapter. The extractor itself is the CLI code."""
    import requests
    from engines import fbX_dl as eng

    session = requests.Session()
    if cookie_path:
        eng.load_cookies_to_session(session, Path(cookie_path))
    media_url, title = eng.extract_facebook_direct(url, session)
    if not media_url:
        return None

    outdir = Path(outdir)
    outdir.mkdir(parents=True, exist_ok=True)
    target = eng.unique_path(outdir / f"{eng.safe_filename(title)}.mp4")
    temp = target.with_suffix(".mp4.part")
    with session.get(media_url, stream=True, timeout=(20, 60), allow_redirects=True) as response:
        response.raise_for_status()
        total = int(response.headers.get("Content-Length") or 0)
        done = 0
        import time
        started = time.time()
        with open(temp, "wb") as handle:
            for chunk in response.iter_content(chunk_size=1024 * 256):
                if not chunk:
                    continue
                handle.write(chunk)
                done += len(chunk)
                elapsed = max(time.time() - started, 0.001)
                speed = int(done / elapsed)
                eta = int((total - done) / speed) if total and speed else None
                pct = (done * 100.0 / total) if total else 0.0
                if callback:
                    callback.onProgress(done, total, speed, eta, pct, "Downloading...")
    temp.replace(target)
    return target


def _redgifs_download(url, choice, outdir, callback):
    from engines import RG_dl as eng
    client = eng.RedGifsClient()
    preferred = str(choice or "best").lower()
    media = client.resolve(url, preferred=preferred if preferred in {"hd", "sd", "gif"} else "best")

    # Reuse the CLI engine's complete download implementation. It already
    # handles API headers, Referer, redirects, content type, .part files and
    # collision-safe output naming. Only its progress renderer is replaced by
    # the Android callback for this invocation.
    original_progress = eng.progress

    def android_progress(downloaded, total, started, speed=None):
        if callback is None:
            return
        import time
        if speed is None:
            speed = downloaded / max(time.time() - started, 0.001)
        eta = ((total - downloaded) / speed) if speed and total else None
        pct = (downloaded * 100.0 / total) if total else 0.0
        callback.onProgress(int(downloaded), int(total), int(speed), int(eta) if eta is not None else None, pct, "Downloading...")

    eng.progress = android_progress
    try:
        output = client.download(media, 1, output_dir=outdir)
    finally:
        eng.progress = original_progress
    return output, media


def _xxxfollow_direct_download(url, outdir, info, callback):
    """Use the original XXXFollow page resolver as the CLI fallback.

    The CLI first tries yt-dlp and then resolves first-party MP4/WebM URLs from
    the page/player JavaScript. Android must retain that fallback instead of
    failing when yt-dlp reports an unavailable format.
    """
    import requests
    import time
    from engines import XF_dl as eng

    session = requests.Session()
    session.headers.update(eng.HEADERS)
    candidates, final_url = eng.resolve_video_urls(session, url)
    if not candidates:
        raise RuntimeError("No media URL was discovered from the XXXFollow page/player data")

    title = (info or {}).get("title") or ""
    if not title:
        try:
            html, _ = eng.fetch_page(session, url)
            from bs4 import BeautifulSoup
            soup = BeautifulSoup(html, "html.parser")
            og = soup.find("meta", attrs={"property": "og:title"})
            title = (og.get("content") if og else None) or (soup.title.get_text(strip=True) if soup.title else None)
        except Exception:
            title = None
    title = eng.safe_filename(title or (info or {}).get("id") or eng.extract_id_from_url(url) or "xxxfollow_video")

    outdir = Path(outdir); outdir.mkdir(parents=True, exist_ok=True)
    last_error = None
    for stream_url, _source in candidates[:12]:
        if ".m3u8" in stream_url.lower().split("?")[0]:
            continue
        try:
            headers = {**eng.HEADERS, "Referer": final_url}
            with session.get(stream_url, headers=headers, stream=True, timeout=(20, 60), allow_redirects=True, verify=True) as response:
                response.raise_for_status()
                content_type = (response.headers.get("Content-Type") or "").lower()
                if "video/" not in content_type and not stream_url.lower().split("?")[0].endswith((".mp4", ".webm", ".m4v", ".mov")):
                    continue
                ext = ".webm" if "webm" in content_type or ".webm" in stream_url.lower().split("?")[0] else ".mp4"
                output = eng.unique_path(outdir / f"{title}{ext}")
                temp = output.with_suffix(output.suffix + ".part")
                total = int(response.headers.get("Content-Length") or 0)
                done = 0
                started = time.time()
                with open(temp, "wb") as handle:
                    for chunk in response.iter_content(chunk_size=1024 * 256):
                        if not chunk:
                            continue
                        handle.write(chunk)
                        done += len(chunk)
                        elapsed = max(time.time() - started, 0.001)
                        speed = int(done / elapsed)
                        eta = int((total - done) / speed) if total and speed else None
                        pct = (done * 100.0 / total) if total else 0.0
                        if callback:
                            callback.onProgress(done, total, speed, eta, pct, "Downloading...")
                if not temp.exists() or temp.stat().st_size <= 0:
                    raise RuntimeError("XXXFollow returned an empty media file")
                temp.replace(output)
                return output
        except Exception as exc:
            last_error = exc
            continue

    raise RuntimeError(f"XXXFollow direct-stream fallback failed: {last_error or 'no valid progressive stream'}")


def _download_xxxfollow(url, mode, selector, outdir, cookie_path, callback, ffmpeg_runner, info):
    """Mirror CLI XXXFollow order: yt-dlp first, first-party direct fallback second."""
    try:
        if mode == "video" and "+" in selector:
            # Do not let yt-dlp attempt the terminal merge because Android does
            # not expose a standalone ffmpeg executable to Python. Download the
            # exact CLI-selected video/audio components separately, then ask the
            # native Android FFmpeg primitive to perform the merge.
            if ffmpeg_runner is None:
                raise RuntimeError("Android FFmpeg runtime is unavailable for XXXFollow video merge")
            video_selector = selector.split("+")[0].strip()
            audio_selector = selector.split("+")[1].strip()
            video_source, _ = _download_component(
                "xxxfollow", url, video_selector, Path(outdir) / "video_source",
                "video", cookie_path, callback, 0.0, 55.0
            )
            audio_source, _ = _download_component(
                "xxxfollow", url, audio_selector, Path(outdir) / "audio_source",
                "audio", cookie_path, callback, 55.0, 80.0
            )
            title = re.sub(r"[\\/:*?\"<>|]+", "_", (info or {}).get("title") or video_source.stem).strip()[:180] or "video"
            output = Path(outdir) / f"{title}.mp4"
            if callback:
                callback.onProgress(video_source.stat().st_size + audio_source.stat().st_size, 0, 0, None, 82.0, "Merging video + audio...")
            ffmpeg_runner.mergeVideoAudio(str(video_source), str(audio_source), str(output))
            if not output.exists() or output.stat().st_size <= 0:
                raise RuntimeError("XXXFollow video/audio merge produced an empty file")
            if callback:
                callback.onProgress(output.stat().st_size, output.stat().st_size, 0, None, 100.0, "Completed")
            return output, "yt-dlp-adaptive"

        source, _ = _download_component(
            "xxxfollow", url, selector, outdir, mode, cookie_path, callback,
            0.0, 72.0 if mode == "audio" else 100.0
        )
        if mode == "video":
            return source, "yt-dlp"

        # The CLI's audio postprocessor is FFmpegExtractAudio. We removed only
        # that terminal postprocessor from the yt-dlp options, so invoke the
        # same MP3 conversion through the Android-native FFmpeg bridge.
        if ffmpeg_runner is None:
            raise RuntimeError("XXXFollow audio requires Android FFmpeg runtime")
        title = re.sub(r"[\\/:*?\"<>|]+", "_", (info or {}).get("title") or source.stem).strip()[:180] or "audio"
        output = Path(outdir) / f"{title}.mp3"
        n = 1
        while output.exists():
            output = Path(outdir) / f"{title}_{n}.mp3"; n += 1
        if callback:
            callback.onProgress(source.stat().st_size, source.stat().st_size, 0, None, 75.0, "Converting audio to MP3...")
        ffmpeg_runner.extractAudio(str(source), str(output), 192)
        if not output.exists() or output.stat().st_size <= 0:
            raise RuntimeError("XXXFollow source contains no usable audio track")
        if callback:
            callback.onProgress(output.stat().st_size, output.stat().st_size, 0, None, 100.0, "Completed")
        return output, "yt-dlp-audio"
    except Exception as ytdlp_error:
        # The CLI's fallback is direct page/player extraction. For audio, the
        # source video is downloaded and then converted to MP3 by native FFmpeg.
        source = _xxxfollow_direct_download(url, Path(outdir) / "direct_source", info, callback)
        if mode == "video":
            if callback:
                callback.onProgress(source.stat().st_size, source.stat().st_size, 0, None, 100.0, "Completed")
            return source, "xxxFollow-direct"
        if ffmpeg_runner is None:
            raise RuntimeError(f"XXXFollow audio requires Android FFmpeg runtime: {ytdlp_error}")
        title = re.sub(r"[\\/:*?\"<>|]+", "_", (info or {}).get("title") or source.stem).strip()[:180] or "audio"
        output = Path(outdir) / f"{title}.mp3"
        n = 1
        while output.exists():
            output = Path(outdir) / f"{title}_{n}.mp3"; n += 1
        if callback:
            callback.onProgress(source.stat().st_size, source.stat().st_size, 0, None, 75.0, "Converting audio to MP3...")
        ffmpeg_runner.extractAudio(str(source), str(output), 192)
        if not output.exists() or output.stat().st_size <= 0:
            raise RuntimeError("XXXFollow source contains no usable audio track")
        if callback:
            callback.onProgress(output.stat().st_size, output.stat().st_size, 0, None, 100.0, "Completed")
        return output, "xxxFollow-direct-audio"


def download_media(url, platform, mode, format_selector=None, output_dir=None, cookie_path=None, callback=None, prefer_direct=False, ffmpeg_runner=None):
    """Run the real Python downloader workflow and return the final local file.

    ``output_dir`` is an app-private staging directory. Android copies the
    finished result to the user-selected SAF destination after this function
    returns. Python owns extraction, quality selection, network transfer,
    merge/conversion decisions and finalization.
    """
    platform = (platform or "").lower()
    mode = (mode or "video").lower()
    url = str(url).strip()
    outdir = Path(output_dir or ".").resolve()
    outdir.mkdir(parents=True, exist_ok=True)
    if not url:
        raise ValueError("URL is empty")

    if platform == "redgifs":
        media_choice = str(format_selector or "best").lower()
        path, media = _redgifs_download(url, media_choice, outdir, callback)
        if mode == "video":
            return json.dumps({
                "path": str(path), "title": media.get("title") or path.stem,
                "extension": path.suffix.lstrip("."), "source": "redgifs-cli-engine",
            }, separators=(",", ":"))
        if ffmpeg_runner is None:
            raise RuntimeError("Android FFmpeg runtime is unavailable for RedGIFs audio")
        title = re.sub(r"[\\/:*?\"<>|]+", "_", media.get("title") or path.stem).strip()[:180] or "audio"
        audio_dir = outdir / "audio"
        audio_dir.mkdir(parents=True, exist_ok=True)
        output = audio_dir / f"{title}.mp3"
        n = 1
        while output.exists():
            output = audio_dir / f"{title}_{n}.mp3"; n += 1
        if callback:
            callback.onProgress(path.stat().st_size, path.stat().st_size, 0, None, 75.0, "Converting audio to MP3...")
        ffmpeg_runner.extractAudio(str(path), str(output), 192)
        if not output.exists() or output.stat().st_size <= 0:
            raise RuntimeError("RedGIFs source contains no usable audio track")
        if callback:
            callback.onProgress(output.stat().st_size, output.stat().st_size, 0, None, 100.0, "Completed")
        return json.dumps({
            "path": str(output), "title": title, "extension": "mp3", "source": "redgifs-cli-audio"
        }, separators=(",", ":"))

    try:
        normalized, info = _format_probe(url, platform, cookie_path)
    except Exception:
        if platform != "xxxfollow":
            raise
        normalized, info = url, {}
    if not info and platform != "xxxfollow":
        raise RuntimeError("Extractor returned no media information")

    choice = str(format_selector or "best")
    selector = _selector_for_choice(platform, mode, choice)

    if platform == "xxxfollow":
        try:
            source, source_kind = _download_xxxfollow(
                normalized, mode, selector, outdir, cookie_path, callback, ffmpeg_runner, info
            )
            if mode == "video":
                title = info.get("title") or source.stem
                return json.dumps({
                    "path": str(source), "title": title, "extension": source.suffix.lstrip("."),
                    "source": source_kind
                }, separators=(",", ":"))
            title = info.get("title") or source.stem
            return json.dumps({
                "path": str(source), "title": title, "extension": source.suffix.lstrip("."),
                "source": source_kind
            }, separators=(",", ":"))
        except Exception as exc:
            raise RuntimeError(f"XXXFollow download failed: {exc}") from exc

    # Preserve the CLI Facebook best-video direct-stream preference.
    if platform == "facebook" and mode == "video" and prefer_direct and choice == "best":
        try:
            direct = _download_facebook_direct(normalized, outdir, cookie_path, callback)
            if direct:
                return json.dumps({
                    "path": str(direct), "title": info.get("title") or direct.stem,
                    "extension": direct.suffix.lstrip("."), "source": "facebook-cli-direct",
                }, separators=(",", ":"))
        except Exception:
            # Match the CLI behavior: direct fallback failure falls through to
            # the normal yt-dlp path.
            pass

    if mode == "audio":
        # Download the actual source audio with the same selector semantics as
        # the CLI, then let Python request the Android-native FFmpeg primitive
        # for the CLI's MP3 post-processing step.
        source, _ = _download_component(platform, normalized, selector, outdir / "audio_source", "audio", cookie_path, callback, 0.0, 72.0)
        if ffmpeg_runner is None:
            raise RuntimeError("Android FFmpeg runtime is unavailable")
        title = re.sub(r"[\\/:*?\"<>|]+", "_", info.get("title") or source.stem).strip()[:180] or "audio"
        output = outdir / f"{title}.mp3"
        if callback:
            callback.onProgress(source.stat().st_size, source.stat().st_size, 0, None, 75.0, "Converting audio to MP3...")
        ffmpeg_runner.extractAudio(str(source), str(output), 192)
        if not output.exists() or output.stat().st_size <= 0:
            raise RuntimeError("MP3 conversion produced an empty file")
        if callback:
            callback.onProgress(output.stat().st_size, output.stat().st_size, 0, None, 100.0, "Completed")
        return json.dumps({"path": str(output), "title": title, "extension": "mp3", "source": "python-cli-audio"}, separators=(",", ":"))

    chosen = _chosen_format(info, selector)
    has_audio = bool(chosen and chosen.get("acodec") not in (None, "none"))
    is_adaptive = "+" in selector or not has_audio

    if not is_adaptive:
        source, _ = _download_component(platform, normalized, selector, outdir, "video", cookie_path, callback, 0.0, 100.0)
        if callback:
            callback.onProgress(source.stat().st_size, source.stat().st_size, 0, None, 100.0, "Completed")
        return json.dumps({"path": str(source), "title": info.get("title") or source.stem, "extension": source.suffix.lstrip("."), "source": "python-cli-video"}, separators=(",", ":"))

    if ffmpeg_runner is None:
        raise RuntimeError("Android FFmpeg runtime is unavailable for adaptive video merge")

    # The CLI asks yt-dlp for bestvideo+bestaudio. On Android the same Python
    # selector is resolved into its two real streams and the Python adapter
    # then asks the native FFmpeg primitive to perform the exact mux step.
    video_selector = selector.split("+")[0].strip()
    audio_selector = selector.split("+")[1].strip() if "+" in selector else "bestaudio/best"
    video_source, _ = _download_component(platform, normalized, video_selector, outdir / "video_source", "video", cookie_path, callback, 0.0, 55.0)
    audio_source, _ = _download_component(platform, normalized, audio_selector, outdir / "audio_source", "audio", cookie_path, callback, 55.0, 80.0)

    title = re.sub(r"[\\/:*?\"<>|]+", "_", info.get("title") or video_source.stem).strip()[:180] or "video"
    output = outdir / f"{title}.mp4"
    if callback:
        callback.onProgress(video_source.stat().st_size + audio_source.stat().st_size, 0, 0, None, 82.0, "Merging video + audio...")
    ffmpeg_runner.mergeVideoAudio(str(video_source), str(audio_source), str(output))
    if not output.exists() or output.stat().st_size <= 0:
        raise RuntimeError("Video/audio merge produced an empty file")
    if callback:
        callback.onProgress(output.stat().st_size, output.stat().st_size, 0, None, 100.0, "Completed")
    return json.dumps({"path": str(output), "title": title, "extension": "mp4", "source": "python-cli-adaptive-video"}, separators=(",", ":"))
