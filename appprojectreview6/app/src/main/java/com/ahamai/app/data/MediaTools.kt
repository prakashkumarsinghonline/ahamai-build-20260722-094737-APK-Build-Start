package com.ahamai.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Image manipulation tools. Operations run with Pillow inside the cloud sandbox (reliable, no
 * device-side native deps) and the edited image is pulled back into the project.
 *
 * Supported ops (IMAGE_EDIT <input> <op> <args> <output>):
 *   resize WxH | scale 50% | thumbnail 256 | crop x,y,w,h | rotate 90 | flip h|v |
 *   grayscale | invert | blur 2 | brightness 1.3 | contrast 1.2 |
 *   convert (change format via output extension) | compress 70 (jp/webp quality) |
 *   text "TEXT|bottom|28|white" | watermark "TEXT"
 */
object MediaTools {

    private val PY_SCRIPT: String = """
        import sys
        from PIL import Image, ImageOps, ImageFilter, ImageEnhance, ImageDraw, ImageFont

        inp, outp, op = sys.argv[1], sys.argv[2], sys.argv[3].lower()
        args = sys.argv[4] if len(sys.argv) > 4 else ""
        img = Image.open(inp)

        def font(size):
            try:
                return ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", size)
            except Exception:
                return ImageFont.load_default()

        if op == "resize":
            w, h = args.lower().split("x"); img = img.resize((int(w), int(h)))
        elif op == "scale":
            p = float(args.replace("%", "")) / 100.0
            img = img.resize((max(1, int(img.width * p)), max(1, int(img.height * p))))
        elif op == "thumbnail":
            m = int(args or 256); img.thumbnail((m, m))
        elif op == "crop":
            x, y, w, h = [int(v) for v in args.split(",")]; img = img.crop((x, y, x + w, y + h))
        elif op == "rotate":
            img = img.rotate(-float(args or 90), expand=True)
        elif op == "flip":
            img = ImageOps.mirror(img) if args.lower().startswith("h") else ImageOps.flip(img)
        elif op in ("grayscale", "greyscale"):
            img = ImageOps.grayscale(img)
        elif op == "invert":
            img = ImageOps.invert(img.convert("RGB"))
        elif op == "blur":
            img = img.filter(ImageFilter.GaussianBlur(float(args or 2)))
        elif op == "brightness":
            img = ImageEnhance.Brightness(img).enhance(float(args or 1.2))
        elif op == "contrast":
            img = ImageEnhance.Contrast(img).enhance(float(args or 1.2))
        elif op in ("text", "watermark"):
            parts = (args.split("|") + ["", "", "", ""])[:4]
            txt = parts[0]; pos = (parts[1] or "bottom").lower()
            size = int(parts[2] or 28); color = parts[3] or "white"
            base = img.convert("RGBA"); d = ImageDraw.Draw(base); f = font(size)
            tw = d.textlength(txt, font=f); th = size
            x = (base.width - tw) // 2
            y = base.height - th - 12 if "bottom" in pos else (12 if "top" in pos else (base.height - th) // 2)
            if "left" in pos: x = 12
            if "right" in pos: x = base.width - tw - 12
            if op == "watermark":
                d.text((x, y), txt, font=f, fill=(255, 255, 255, 128))
            else:
                d.rectangle([x - 6, y - 4, x + tw + 6, y + th + 6], fill=(0, 0, 0, 120))
                d.text((x, y), txt, font=f, fill=color)
            img = base.convert("RGB")
        elif op in ("convert", "compress"):
            pass
        else:
            print("UNKNOWN_OP:" + op); sys.exit(2)

        kw = {}
        ext = outp.rsplit(".", 1)[-1].lower()
        if ext in ("jpg", "jpeg", "webp"):
            img = img.convert("RGB")
            kw["quality"] = int(args or 70) if op == "compress" else 90
            kw["optimize"] = True
        img.save(outp, **kw)
        print("OK", outp, img.width, "x", img.height)
    """.trimIndent()

    private fun shq(s: String) = "'" + s.replace("'", "'\\''") + "'"

    /**
     * VISION (local, free): "see" an image without any paid AI vision API. Runs tesseract OCR to
     * pull out any text, plus ImageMagick metadata (dimensions/format) and a dominant-colour
     * summary — all inside the cloud sandbox. Perfect for screenshots, documents, error dialogs,
     * receipts, UI mockups, etc.
     */
    suspend fun analyzeImage(ctx: Context, projectDir: String, inRel: String): String = withContext(Dispatchers.IO) {
        val f = File(projectDir, inRel)
        if (!f.exists() || !f.isFile) return@withContext "ERROR: image not found in project: $inRel"
        if (!CloudTools.pushProjectFile(ctx, projectDir, inRel))
            return@withContext "ERROR: failed to upload image to the sandbox."
        CloudTools.ensureCmd(ctx, projectDir, "tesseract", "aham_apt tesseract-ocr", 300)
            ?.let { return@withContext "ERROR: $it" }
        val p = shq("/workspace/$inRel")
        val res = CloudTools.execProv(ctx, projectDir,
            "echo '== IMAGE INFO =='; identify -format '%wx%h  %m  %b\\n' $p 2>/dev/null | head -1; " +
            "echo; echo '== DOMINANT COLOURS =='; convert $p -resize 80x80 -colors 5 -unique-colors -format '%c' histogram:info: 2>/dev/null | sed 's/^ *//' | head -5; " +
            "echo; echo '== TEXT IN IMAGE (OCR) =='; tesseract $p - 2>/dev/null | sed '/^[[:space:]]*\$/d' | head -250; echo '--- vision done ---'",
            200)
        "IMAGE ANALYSIS of $inRel (local OCR + metadata):\n${res.formatted(6000)}"
    }

    /**
     * Image understanding that prefers the configured VISION model (e.g. Pixtral) and only
     * falls back to local OCR ([analyzeImage]) if the vision model is unavailable or returns
     * an unsatisfactory (empty / error / refusal) result.
     */
    suspend fun analyzeImageVisionFirst(ctx: Context, projectDir: String, inRel: String): String = withContext(Dispatchers.IO) {
        val file = ProjectManager.resolveFile(projectDir, inRel) ?: File(projectDir, inRel)
        if (!file.exists() || !file.isFile) return@withContext "ERROR: image not found in project: $inRel"

        // Prefer the dedicated vision endpoint; in custom mode use the active chat endpoint.
        val ep = ApiConfig.vision(ctx) ?: ApiConfig.chat(ctx)
        val dataUrl = ImageUtils.fileToDataUrl(file.absolutePath, maxDim = 1024, quality = 82)
        if (dataUrl != null && ep.baseUrl.isNotBlank() && ep.apiKey.isNotBlank() && ep.model.isNotBlank()) {
            try {
                val prompt = "Look at this image and describe it thoroughly: read ALL text verbatim, " +
                    "identify objects/UI/diagrams/errors, and note anything important. Be detailed and accurate."
                val msgs = listOf(ApiMessage("user", prompt, listOf(dataUrl)))
                val sb = StringBuilder()
                ApiClient.streamChatVision(ep.baseUrl, ep.apiKey, ep.model, msgs).collect { d ->
                    d.text?.let { sb.append(it) }
                }
                val text = sb.toString().trim()
                if (isSatisfactory(text)) {
                    return@withContext "IMAGE ANALYSIS of $inRel (vision model: ${ep.model}):\n$text"
                }
            } catch (_: Exception) {
                // fall through to OCR
            }
        }
        // Fallback: local OCR + metadata in the cloud sandbox.
        analyzeImage(ctx, projectDir, inRel)
    }

    /** Heuristic: did the vision model actually "see" the image (not blank / error / refusal)? */
    private fun isSatisfactory(text: String): Boolean {
        if (text.length < 12) return false
        val low = text.lowercase()
        if (low.startsWith("error")) return false
        val refusals = listOf(
            "can't see", "cannot see", "can not see", "unable to see", "i cannot view",
            "i can't view", "no image", "couldn't process the image", "i don't have the ability to see"
        )
        return refusals.none { low.contains(it) }
    }

    suspend fun imageEdit(
        ctx: Context, projectDir: String, inRel: String, op: String, args: String, outRel: String
    ): String = withContext(Dispatchers.IO) {
        val input = File(projectDir, inRel)
        if (!input.exists() || !input.isFile) return@withContext "ERROR: image not found in project: $inRel"
        val out = outRel.ifBlank { inRel.substringBeforeLast('.') + "_edited." + inRel.substringAfterLast('.', "png") }

        CloudTools.ensurePython(ctx, projectDir, "PIL", "pillow")?.let { return@withContext "ERROR: $it" }
        // Make sure a TrueType font is available for text/watermark (Pillow default is tiny).
        if (op.lowercase() in listOf("text", "watermark")) {
            CloudTools.execProv(ctx, projectDir, "fc-list 2>/dev/null | grep -qi dejavu || aham_apt fonts-dejavu-core", 180)
        }

        // Upload the input image (sync skips large binaries) + the Pillow script.
        if (!CloudTools.pushProjectFile(ctx, projectDir, inRel))
            return@withContext "ERROR: failed to upload image to the sandbox."
        val scriptFile = File(projectDir, "._imgedit_${System.currentTimeMillis()}.py")
        scriptFile.writeText(PY_SCRIPT)
        try {
            CloudTools.pushProjectFile(ctx, projectDir, scriptFile.name)
            val res = CloudTools.execIn(ctx, projectDir,
                "python3 ${shq("/workspace/" + scriptFile.name)} ${shq("/workspace/$inRel")} ${shq("/workspace/$out")} ${shq(op)} ${shq(args)} 2>&1",
                120)
            if (!res.stdout.contains("OK ") && !res.stdout.contains("OK\t")) {
                return@withContext "IMAGE EDIT failed:\n${res.formatted(1500)}"
            }
            val pull = CloudTools.cloudPull(ctx, projectDir, "/workspace/$out", out)
            "IMAGE EDIT ($op ${args.take(40)}) → project/$out\n$pull\n${res.stdout.take(300)}"
        } finally {
            scriptFile.delete()
        }
    }
}
