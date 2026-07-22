package com.ahamai.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Best-in-class bug-bounty / offensive-security toolkit, all running inside the E2B cloud sandbox.
 *
 * Every tool self-provisions (installs its binary on first use via CloudTools' aham_apt / aham_gh
 * helpers) and returns a concise, model-readable report. The heavy hitters used here are the same
 * ones professional bug hunters rely on:
 *
 *   recon       subfinder + dnsx + httpx + whatweb + wafw00f + nmap   (attack-surface mapping)
 *   portScan    nmap -sV                                              (service/version detection)
 *   vulnScan    nuclei (5000+ community templates)                    (known-CVE / misconfig scan)
 *   dirFuzz     ffuf + a wordlist                                     (content / endpoint discovery)
 *   niktoScan   nikto                                                 (web-server misconfig scan)
 *   sqliTest    sqlmap                                                (SQL-injection testing)
 *   sslScan     sslscan / testssl.sh                                  (TLS/cert hardening review)
 *   urlHarvest  gau + waybackurls                                     (historical URL collection)
 *   sast        semgrep --config auto                                 (source-code static analysis)
 *   repoSecrets gitleaks                                              (hardcoded-secret detection)
 *
 * These are intended for AUTHORIZED security testing / bug-bounty work on assets the user owns or
 * is permitted to test.
 */
object CyberTools {

    /** host[:port] without scheme or path, for tools that take a bare host. */
    private fun hostOf(target: String): String =
        target.trim().removePrefix("https://").removePrefix("http://").substringBefore('/').ifBlank { target.trim() }

    /** apex/registrable domain (best-effort) for subdomain enumeration. */
    private fun domainOf(target: String): String = hostOf(target).substringBefore(':')

    /** Full recon sweep: subdomains, DNS, live hosts, tech fingerprint, WAF, open ports. */
    suspend fun recon(ctx: Context, projectDir: String, target: String): String = withContext(Dispatchers.IO) {
        val domain = domainOf(target)
        if (domain.isBlank()) return@withContext "ERROR: no target domain provided."
        CloudTools.ensureCmd(ctx, projectDir, "subfinder", "aham_gh projectdiscovery/subfinder 'linux_amd64.zip' subfinder", 300)
        CloudTools.ensureCmd(ctx, projectDir, "httpx", "aham_gh projectdiscovery/httpx 'linux_amd64.zip' httpx", 300)
        CloudTools.ensureCmd(ctx, projectDir, "dnsx", "aham_gh projectdiscovery/dnsx 'linux_amd64.zip' dnsx", 300)
        CloudTools.ensureCmd(ctx, projectDir, "whatweb", "aham_apt whatweb", 200)
        CloudTools.ensureCmd(ctx, projectDir, "wafw00f", "aham_apt wafw00f", 200)
        CloudTools.ensureCmd(ctx, projectDir, "nmap", "aham_apt nmap", 200)

        val res = CloudTools.execProv(ctx, projectDir,
            "echo '== SUBDOMAINS (subfinder) =='; subfinder -d '$domain' -silent -timeout 8 2>/dev/null | tee /tmp/subs.txt | head -60; " +
            "echo; echo '== LIVE HOSTS (httpx) =='; (cat /tmp/subs.txt 2>/dev/null; echo '$domain') | sort -u | httpx -silent -title -status-code -tech-detect -no-color 2>/dev/null | head -60; " +
            "echo; echo '== DNS (dnsx) =='; echo '$domain' | dnsx -silent -a -resp -no-color 2>/dev/null | head -15; " +
            "echo; echo '== TECH (whatweb) =='; whatweb --no-errors -a 1 '$domain' 2>/dev/null | head -15; " +
            "echo; echo '== WAF (wafw00f) =='; wafw00f '$domain' 2>/dev/null | grep -iE 'is behind|seems|No WAF' | head -5; " +
            "echo; echo '== TOP PORTS (nmap) =='; nmap -sV -T4 --top-ports 50 -Pn '$domain' 2>/dev/null | grep -E '^[0-9]+/|open' | head -40",
            420)
        "RECON for $domain:\n${res.formatted(7000)}"
    }

    /** nmap service/version scan. [opts] lets the model pass extra flags (e.g. "-p 1-1000 -A"). */
    suspend fun portScan(ctx: Context, projectDir: String, target: String, opts: String): String = withContext(Dispatchers.IO) {
        val host = hostOf(target)
        CloudTools.ensureCmd(ctx, projectDir, "nmap", "aham_apt nmap", 240)?.let { return@withContext "ERROR: $it" }
        val flags = opts.ifBlank { "-sV -T4 --top-ports 200 -Pn" }
        val res = CloudTools.execProv(ctx, projectDir, "nmap $flags '$host' 2>&1 | head -120", 400)
        "PORT SCAN ($flags) of $host:\n${res.formatted(5000)}"
    }

    /** nuclei vulnerability scan. [opts] can pass tags/severity (e.g. "-severity high,critical -tags cve"). */
    suspend fun vulnScan(ctx: Context, projectDir: String, target: String, opts: String): String = withContext(Dispatchers.IO) {
        CloudTools.ensureCmd(ctx, projectDir, "nuclei", "aham_gh projectdiscovery/nuclei 'linux_amd64.zip' nuclei", 300)
            ?.let { return@withContext "ERROR: $it" }
        val flags = opts.ifBlank { "-severity low,medium,high,critical" }
        // First run also fetches the template store (-update-templates) quietly.
        val res = CloudTools.execProv(ctx, projectDir,
            "nuclei -update-templates -silent >/dev/null 2>&1; nuclei -u '${target.trim()}' $flags -silent -no-color 2>&1 | head -120; echo '--- nuclei done ---'",
            600)
        "VULN SCAN of ${target.trim()} ($flags):\n${res.formatted(6000)}"
    }

    /** ffuf content/endpoint discovery. [wordlistUrl] optional custom wordlist URL. */
    suspend fun dirFuzz(ctx: Context, projectDir: String, target: String, wordlistUrl: String): String = withContext(Dispatchers.IO) {
        CloudTools.ensureCmd(ctx, projectDir, "ffuf", "aham_gh ffuf/ffuf 'linux_amd64.tar.gz' ffuf", 300)?.let { return@withContext "ERROR: $it" }
        // Wordlist: dirb's common list (apt) by default, or a user-supplied URL.
        val wl = if (wordlistUrl.isNotBlank())
            "curl -sL '${wordlistUrl.trim()}' -o /tmp/wl.txt"
        else
            "aham_apt dirb >/dev/null 2>&1; cp /usr/share/dirb/wordlists/common.txt /tmp/wl.txt 2>/dev/null || (curl -sL https://raw.githubusercontent.com/v0re/dirb/master/wordlists/common.txt -o /tmp/wl.txt)"
        val url = target.trim().let { if (it.contains("FUZZ")) it else it.trimEnd('/') + "/FUZZ" }
        val res = CloudTools.execProv(ctx, projectDir,
            "$wl; wc -l < /tmp/wl.txt | xargs echo 'wordlist size:'; ffuf -w /tmp/wl.txt -u '$url' -mc 200,201,202,204,301,302,307,401,403 -t 50 -s 2>/dev/null | head -80; echo '--- ffuf done ---'",
            420)
        "DIR FUZZ of $url:\n${res.formatted(5000)}"
    }

    /** nikto web-server misconfiguration scan (git-installed; not an apt package on Debian). */
    suspend fun niktoScan(ctx: Context, projectDir: String, target: String): String = withContext(Dispatchers.IO) {
        CloudTools.ensureCmd(ctx, projectDir, "nikto",
            "aham_apt perl libnet-ssleay-perl libjson-perl libxml-writer-perl; rm -rf /opt/nikto; git clone -q --depth 1 https://github.com/sullo/nikto /opt/nikto; " +
            "printf '#!/bin/bash\\nperl /opt/nikto/program/nikto.pl \"\$@\"\\n' > /usr/local/bin/nikto; chmod +x /usr/local/bin/nikto", 300)
            ?.let { return@withContext "ERROR: $it" }
        val res = CloudTools.execProv(ctx, projectDir,
            "nikto -h '${target.trim()}' -maxtime 120s -nointeractive 2>&1 | grep -vE '^- ' | head -80; echo '--- nikto done ---'", 240)
        "NIKTO SCAN of ${target.trim()}:\n${res.formatted(5000)}"
    }

    /** sqlmap SQL-injection test. [opts] passes extra flags (e.g. "--data 'id=1' --level 2"). */
    suspend fun sqliTest(ctx: Context, projectDir: String, target: String, opts: String): String = withContext(Dispatchers.IO) {
        CloudTools.ensureCmd(ctx, projectDir, "sqlmap", "aham_apt sqlmap", 240)?.let { return@withContext "ERROR: $it" }
        val flags = opts.ifBlank { "--batch --random-agent --level 1 --risk 1" }
        val res = CloudTools.execProv(ctx, projectDir,
            "sqlmap -u '${target.trim()}' $flags --flush-session -v 1 2>&1 | grep -vE '^\\[.\\] (starting|ending)' | tail -90; echo '--- sqlmap done ---'", 420)
        "SQLi TEST of ${target.trim()} ($flags):\n${res.formatted(5500)}"
    }

    /** sslscan TLS/certificate review. */
    suspend fun sslScan(ctx: Context, projectDir: String, target: String): String = withContext(Dispatchers.IO) {
        CloudTools.ensureCmd(ctx, projectDir, "sslscan", "aham_apt sslscan", 200)?.let { return@withContext "ERROR: $it" }
        val host = hostOf(target).let { if (it.contains(':')) it else "$it:443" }
        val res = CloudTools.execProv(ctx, projectDir,
            "sslscan --no-colour '$host' 2>&1 | head -90; echo '--- sslscan done ---'", 180)
        "SSL/TLS SCAN of $host:\n${res.formatted(5000)}"
    }

    /** Historical + live URL harvesting via gau (passive: Wayback/CommonCrawl/OTX) + katana (crawl). */
    suspend fun urlHarvest(ctx: Context, projectDir: String, target: String): String = withContext(Dispatchers.IO) {
        val domain = domainOf(target)
        CloudTools.ensureCmd(ctx, projectDir, "gau", "aham_gh lc/gau 'linux_amd64.tar.gz' gau", 300)
        CloudTools.ensureCmd(ctx, projectDir, "waybackurls", "aham_gh tomnomnom/waybackurls 'linux-amd64' waybackurls", 240)
        CloudTools.ensureCmd(ctx, projectDir, "katana", "aham_gh projectdiscovery/katana 'linux_amd64.zip' katana", 300)
        val res = CloudTools.execProv(ctx, projectDir,
            "( timeout 45 gau '$domain' 2>/dev/null; echo '$domain' | timeout 45 waybackurls 2>/dev/null; timeout 45 katana -u 'https://$domain' -silent -d 2 -nc 2>/dev/null ) | sort -u | tee /tmp/urls.txt | head -150; " +
            "echo; echo \"total unique URLs: \$(wc -l < /tmp/urls.txt 2>/dev/null)\"", 360)
        "URL HARVEST for $domain (gau + waybackurls + katana):\n${res.formatted(6000)}"
    }

    // ---- Advanced web-security tools (researched + verified in E2B) ----

    /** dalfox — dedicated XSS scanner/verifier (v3 syntax: `dalfox url --url <URL>`). */
    suspend fun xssScan(ctx: Context, projectDir: String, target: String): String = withContext(Dispatchers.IO) {
        CloudTools.ensureCmd(ctx, projectDir, "dalfox", "aham_gh hahwul/dalfox 'linux-x86_64.tar.gz' dalfox", 300)?.let { return@withContext "ERROR: $it" }
        val res = CloudTools.execProv(ctx, projectDir,
            "timeout 220 dalfox url --url '${target.trim()}' --silence --no-spinner --skip-mining-dom 2>&1 | sed -E 's/\\x1b\\[[0-9;]*m//g' | tail -70; echo '--- dalfox done ---'", 280)
        "XSS SCAN (dalfox) of ${target.trim()}:\n${res.formatted(5500)}"
    }

    /** commix — automated command-injection testing. */
    suspend fun cmdiTest(ctx: Context, projectDir: String, target: String, opts: String): String = withContext(Dispatchers.IO) {
        CloudTools.ensureCmd(ctx, projectDir, "commix",
            "rm -rf /opt/commix; git clone -q --depth 1 https://github.com/commixproject/commix /opt/commix; " +
            "printf '#!/bin/bash\\npython3 /opt/commix/commix.py \"\$@\"\\n' > /usr/local/bin/commix; chmod +x /usr/local/bin/commix", 240)?.let { return@withContext "ERROR: $it" }
        val flags = opts.ifBlank { "--batch --level 1" }
        val res = CloudTools.execProv(ctx, projectDir,
            "timeout 220 commix -u '${target.trim()}' $flags 2>&1 | sed -E 's/\\x1b\\[[0-9;]*m//g' | grep -iE 'injectable|vulnerable|payload|parameter|technique|not' | head -60; echo '--- commix done ---'", 280)
        "COMMAND-INJECTION TEST (commix) of ${target.trim()} ($flags):\n${res.formatted(5500)}"
    }

    /** crlfuzz — CRLF injection / response-splitting. */
    suspend fun crlfScan(ctx: Context, projectDir: String, target: String): String = withContext(Dispatchers.IO) {
        CloudTools.ensureCmd(ctx, projectDir, "crlfuzz", "aham_gh dwisiswant0/crlfuzz 'linux_amd64.tar.gz' crlfuzz", 300)?.let { return@withContext "ERROR: $it" }
        val res = CloudTools.execProv(ctx, projectDir,
            "timeout 150 crlfuzz -u '${target.trim()}' -s 2>&1 | sed -E 's/\\x1b\\[[0-9;]*m//g' | head -60; echo '--- crlfuzz done ---'", 200)
        "CRLF SCAN (crlfuzz) of ${target.trim()}:\n${res.formatted(4500)}"
    }

    /** arjun — hidden HTTP parameter discovery. */
    suspend fun paramDiscover(ctx: Context, projectDir: String, target: String): String = withContext(Dispatchers.IO) {
        CloudTools.ensureCmd(ctx, projectDir, "arjun", "aham_pip arjun", 300)?.let { return@withContext "ERROR: $it" }
        val res = CloudTools.execProv(ctx, projectDir,
            "rm -f /tmp/arjun.json; timeout 200 arjun -u '${target.trim()}' -oJ /tmp/arjun.json -q 2>&1 | tail -5; echo '--- params ---'; cat /tmp/arjun.json 2>/dev/null | head -40; echo '--- arjun done ---'", 260)
        "PARAMETER DISCOVERY (arjun) of ${target.trim()}:\n${res.formatted(4500)}"
    }

    /** feroxbuster — fast recursive content discovery. */
    suspend fun contentDiscover(ctx: Context, projectDir: String, target: String): String = withContext(Dispatchers.IO) {
        CloudTools.ensureCmd(ctx, projectDir, "feroxbuster", "aham_gh epi052/feroxbuster 'x86_64-linux-feroxbuster.tar.gz' feroxbuster", 300)?.let { return@withContext "ERROR: $it" }
        val res = CloudTools.execProv(ctx, projectDir,
            "timeout 200 feroxbuster -u '${target.trim()}' -q -d 2 --time-limit 170s -s 200,204,301,302,307,401,403 2>&1 | sed -E 's/\\x1b\\[[0-9;]*m//g' | head -80; echo '--- feroxbuster done ---'", 260)
        "CONTENT DISCOVERY (feroxbuster) of ${target.trim()}:\n${res.formatted(5500)}"
    }

    /** naabu — fast port scanner (connect mode; needs libpcap for SYN). */
    suspend fun fastPortScan(ctx: Context, projectDir: String, target: String): String = withContext(Dispatchers.IO) {
        CloudTools.ensureCmd(ctx, projectDir, "naabu",
            "aham_apt libpcap-dev >/dev/null 2>&1; aham_gh projectdiscovery/naabu 'linux_amd64.zip' naabu", 300)?.let { return@withContext "ERROR: $it" }
        val host = hostOf(target)
        val res = CloudTools.execProv(ctx, projectDir,
            "timeout 180 naabu -host '$host' -silent -top-ports 1000 2>&1 | head -80; echo '--- naabu done ---'", 240)
        "FAST PORT SCAN (naabu) of $host:\n${res.formatted(4000)}"
    }

    /** trufflehog — deep, *verified* secret scanning over a project path (git-aware). */
    suspend fun deepSecrets(ctx: Context, projectDir: String, targetRel: String): String = withContext(Dispatchers.IO) {
        CloudTools.ensureCmd(ctx, projectDir, "trufflehog", "aham_gh trufflesecurity/trufflehog 'linux_amd64.tar.gz' trufflehog", 300)?.let { return@withContext "ERROR: $it" }
        CloudTools.syncProjectUp(ctx, projectDir)
        val path = "/workspace/" + targetRel.trim().removePrefix("/")
        val res = CloudTools.execProv(ctx, projectDir,
            "timeout 220 trufflehog filesystem '$path' --no-update --results=verified,unknown 2>&1 | sed -E 's/\\x1b\\[[0-9;]*m//g' | head -90; echo '--- trufflehog done ---'", 280)
        "DEEP SECRET SCAN (trufflehog) of ${targetRel.ifBlank { "project" }}:\n${res.formatted(6000)}"
    }

    /** wpscan — WordPress vulnerability scanner. */
    suspend fun wpScan(ctx: Context, projectDir: String, target: String): String = withContext(Dispatchers.IO) {
        CloudTools.ensureCmd(ctx, projectDir, "wpscan",
            "aham_apt ruby-dev build-essential libcurl4-openssl-dev libxml2-dev libxslt1-dev zlib1g-dev >/dev/null 2>&1; gem install wpscan --no-document >/dev/null 2>&1", 600)?.let { return@withContext "ERROR: $it" }
        val res = CloudTools.execProv(ctx, projectDir,
            "timeout 220 wpscan --url '${target.trim()}' --no-banner --random-user-agent --format cli-no-color 2>&1 | grep -vE '^\\s*$' | head -90; echo '--- wpscan done ---'", 280)
        "WORDPRESS SCAN (wpscan) of ${target.trim()}:\n${res.formatted(6000)}"
    }

    /** semgrep static analysis over a project path (default: whole project). */
    suspend fun sast(ctx: Context, projectDir: String, targetRel: String): String = withContext(Dispatchers.IO) {
        CloudTools.ensureCmd(ctx, projectDir, "semgrep", "aham_pip semgrep", 420)?.let { return@withContext "ERROR: $it" }
        CloudTools.syncProjectUp(ctx, projectDir)
        val path = "/workspace/" + targetRel.trim().removePrefix("/").ifBlank { "" }
        val res = CloudTools.execProv(ctx, projectDir,
            "cd /workspace && semgrep scan --config auto --quiet --no-color --max-target-bytes 2000000 '$path' 2>&1 | head -140; echo '--- semgrep done ---'", 500)
        "SAST (semgrep) of ${targetRel.ifBlank { "project" }}:\n${res.formatted(6000)}"
    }

    /** gitleaks hardcoded-secret scan over a project path or decompiled folder. */
    suspend fun repoSecrets(ctx: Context, projectDir: String, targetRel: String): String = withContext(Dispatchers.IO) {
        CloudTools.ensureCmd(ctx, projectDir, "gitleaks", "aham_gh gitleaks/gitleaks 'linux_x64.tar.gz' gitleaks", 300)?.let { return@withContext "ERROR: $it" }
        CloudTools.syncProjectUp(ctx, projectDir)
        val path = "/workspace/" + targetRel.trim().removePrefix("/")
        val res = CloudTools.execProv(ctx, projectDir,
            "gitleaks detect --source '$path' --no-git --no-banner -f json -r /tmp/leaks.json 2>&1 | tail -5; " +
            "echo '--- findings ---'; cat /tmp/leaks.json 2>/dev/null | head -200", 300)
        "SECRET SCAN (gitleaks) of ${targetRel.ifBlank { "project" }}:\n${res.formatted(6000)}"
    }

    /**
     * Social/username OSINT: find PUBLIC accounts/profiles that exist for a username across 3000+
     * sites using maigret. For authorized footprinting / checking your own (or permitted) handles.
     */
    suspend fun socialOsint(ctx: Context, projectDir: String, username: String): String = withContext(Dispatchers.IO) {
        val u = username.trim().removePrefix("@").filter { it.isLetterOrDigit() || it in "._-" }
        if (u.isBlank()) return@withContext "ERROR: provide a username (e.g. johndoe)."
        CloudTools.ensurePython(ctx, projectDir, "maigret", "maigret")?.let { return@withContext "ERROR: $it" }
        val res = CloudTools.execIn(ctx, projectDir,
            "timeout 230 maigret '$u' --timeout 6 -a --no-progressbar 2>&1 | grep -E '\\[\\+\\]|\u251c\u2500|\u2514\u2500' | sed -E 's/\\x1b\\[[0-9;]*m//g' | head -90; echo '--- osint done ---'",
            300)
        "SOCIAL OSINT for '$u' (public account footprint via maigret):\n${res.formatted(7000)}"
    }

    /**
     * Phone-number OSINT (metadata only): validity, country/region, carrier, line type and
     * timezone via Google's libphonenumber. This is NON-identifying metadata — it does NOT reveal
     * who owns the number. Provide the number with country code, e.g. +9193....
     */
    suspend fun phoneOsint(ctx: Context, projectDir: String, number: String): String = withContext(Dispatchers.IO) {
        val num = number.filter { it.isDigit() || it == '+' }
        if (num.length < 7) return@withContext "ERROR: provide a full number with country code, e.g. +919876543210."
        CloudTools.ensurePython(ctx, projectDir, "phonenumbers", "phonenumbers")?.let { return@withContext "ERROR: $it" }
        val py = """
            import phonenumbers as p
            from phonenumbers import carrier, geocoder, timezone, number_type
            n = p.parse("$num")
            T = {0:"FIXED_LINE",1:"MOBILE",2:"FIXED_OR_MOBILE",3:"TOLL_FREE",10:"VOIP"}
            print("valid:", p.is_valid_number(n))
            print("number:", p.format_number(n, p.PhoneNumberFormat.INTERNATIONAL))
            print("country_code:", n.country_code)
            print("region:", geocoder.description_for_number(n, "en"))
            print("carrier:", carrier.name_for_number(n, "en"))
            print("line_type:", T.get(number_type(n), number_type(n)))
            print("timezones:", ", ".join(timezone.time_zones_for_number(n)))
        """.trimIndent()
        val res = CloudTools.execIn(ctx, projectDir, "python3 -c '$py'", 90)
        "PHONE OSINT for $num (metadata only — not the owner's identity):\n${res.formatted(2500)}"
    }
}
