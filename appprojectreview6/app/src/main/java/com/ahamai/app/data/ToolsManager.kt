package com.ahamai.app.data

/**
 * Represents a tool that can be used by agents to perform specific tasks.
 * Each tool has Python code that runs on Wandbox.
 */
data class Tool(
    val id: String,
    val name: String,
    val description: String,
    val icon: String, // favicon domain or emoji
    val category: String,
    val pythonCode: String // Template with {{INPUT}} placeholder
)

object ToolsManager {

    val CATEGORIES = listOf("Search", "Data", "Utility", "Dev")

    val ALL_TOOLS = listOf(
        Tool(
            id = "web_scraper",
            name = "Web Scraper",
            description = "Scrape any webpage and extract content",
            icon = "globe",
            category = "Search",
            pythonCode = """import subprocess, sys
subprocess.run([sys.executable, '-m', 'pip', 'install', 'beautifulsoup4', '--quiet', '--target', '/tmp/pylibs'], capture_output=True, timeout=30)
sys.path.insert(0, '/tmp/pylibs')
import urllib.request
from bs4 import BeautifulSoup
url = "{{INPUT}}"
req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"})
resp = urllib.request.urlopen(req, timeout=10)
soup = BeautifulSoup(resp.read().decode("utf-8", errors="ignore"), 'html.parser')
[s.decompose() for s in soup(['script','style','nav','footer','header'])]
title = soup.title.string if soup.title else "No title"
text = soup.get_text(separator='\n', strip=True)[:3000]
links = [(a.text.strip(), a.get('href','')) for a in soup.find_all('a', href=True) if a.text.strip()][:10]
print(f"Title: {title}")
print(f"\nContent:\n{text[:2000]}")
print(f"\nLinks ({len(links)}):")
for t, h in links[:10]:
    print(f"  {t[:40]} -> {h[:60]}")"""
        ),
        Tool(
            id = "news_scraper",
            name = "News Headlines",
            description = "Get latest tech news from Hacker News",
            icon = "news.ycombinator.com",
            category = "Search",
            pythonCode = """import subprocess, sys
subprocess.run([sys.executable, '-m', 'pip', 'install', 'beautifulsoup4', '--quiet', '--target', '/tmp/pylibs'], capture_output=True, timeout=30)
sys.path.insert(0, '/tmp/pylibs')
import urllib.request
from bs4 import BeautifulSoup
query = "{{INPUT}}"
html = urllib.request.urlopen(urllib.request.Request("https://news.ycombinator.com", headers={"User-Agent": "Mozilla/5.0"}), timeout=10).read().decode()
soup = BeautifulSoup(html, 'html.parser')
stories = soup.select('.athing')
print(f"Hacker News - Top Stories (query: {query}):\n")
for i, story in enumerate(stories[:15], 1):
    title_el = story.select_one('.titleline > a')
    if title_el:
        title = title_el.text
        url = title_el.get('href', '')
        score_el = story.find_next_sibling('tr')
        score = score_el.select_one('.score').text if score_el and score_el.select_one('.score') else '0 points'
        if query.lower() in title.lower() or query == "" or query == "latest":
            print(f"{i}. {title}")
            print(f"   {score} | {url[:60]}")
            print()"""
        ),
        Tool(
            id = "weather",
            name = "Weather",
            description = "Get current weather for any city",
            icon = "weather",
            category = "Data",
            pythonCode = """import urllib.request, json
city = "{{INPUT}}"
url = f"https://wttr.in/{city.replace(' ','+')}?format=j1"
data = json.loads(urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"}), timeout=10).read().decode())
c = data['current_condition'][0]
print(f"Weather for {city}:")
print(f"  Temperature: {c['temp_C']}°C / {c['temp_F']}°F")
print(f"  Feels like: {c['FeelsLikeC']}°C")
print(f"  Condition: {c['weatherDesc'][0]['value']}")
print(f"  Humidity: {c['humidity']}%")
print(f"  Wind: {c['windspeedKmph']} km/h {c['winddir16Point']}")
print(f"  Visibility: {c['visibility']} km")
print(f"  UV Index: {c['uvIndex']}")
forecast = data.get('weather', [])
if forecast:
    print(f"\nForecast:")
    for day in forecast[:3]:
        print(f"  {day['date']}: {day['mintempC']}°C - {day['maxtempC']}°C, {day['hourly'][4]['weatherDesc'][0]['value']}")"""
        ),
        Tool(
            id = "crypto_price",
            name = "Crypto Price",
            description = "Get live cryptocurrency prices",
            icon = "coingecko.com",
            category = "Data",
            pythonCode = """import urllib.request, json
query = "{{INPUT}}".lower().strip()
coins_map = {"btc":"bitcoin","eth":"ethereum","sol":"solana","doge":"dogecoin","xrp":"ripple","ada":"cardano","dot":"polkadot","matic":"polygon","avax":"avalanche","link":"chainlink"}
if query in coins_map: query = coins_map[query]
ids = query if ',' in query else f"bitcoin,ethereum,solana,{query}" if query not in ["bitcoin","ethereum","solana"] else "bitcoin,ethereum,solana,dogecoin,cardano,ripple"
url = f"https://api.coingecko.com/api/v3/simple/price?ids={ids}&vs_currencies=usd,inr&include_24hr_change=true&include_market_cap=true"
data = json.loads(urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"}), timeout=10).read().decode())
print("Crypto Prices (Live):\n")
for coin, info in data.items():
    price = info.get('usd', 0)
    change = info.get('usd_24h_change', 0)
    mcap = info.get('usd_market_cap', 0)
    arrow = "↑" if change > 0 else "↓"
    print(f"  {coin.upper()}")
    print(f"    Price: ${'$'}{price:,.2f}")
    print(f"    24h: {arrow} {change:.2f}%")
    print(f"    Market Cap: ${'$'}{mcap:,.0f}")
    print()"""
        ),
        Tool(
            id = "stock_price",
            name = "Stock Price",
            description = "Get live stock market prices",
            icon = "finance.yahoo.com",
            category = "Data",
            pythonCode = """import urllib.request, json
symbol = "{{INPUT}}".upper().strip()
if not symbol: symbol = "AAPL"
url = f"https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?interval=1d&range=5d"
data = json.loads(urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"}), timeout=10).read().decode())
meta = data['chart']['result'][0]['meta']
price = meta['regularMarketPrice']
prev_close = meta.get('chartPreviousClose', price)
change = price - prev_close
pct = (change / prev_close) * 100 if prev_close else 0
print(f"Stock: {symbol}")
print(f"  Price: ${'$'}{price:.2f}")
print(f"  Change: {'+'if change>0 else ''}{change:.2f} ({'+'if pct>0 else ''}{pct:.2f}%)")
print(f"  Previous Close: ${'$'}{prev_close:.2f}")
print(f"  Day Range: ${'$'}{meta.get('regularMarketDayLow',0):.2f} - ${'$'}{meta.get('regularMarketDayHigh',0):.2f}")
print(f"  Currency: {meta.get('currency','USD')}")
print(f"  Exchange: {meta.get('exchangeName','')}")"""
        ),
        Tool(
            id = "image_search",
            name = "Image Search",
            description = "Search and get image URLs",
            icon = "image",
            category = "Search",
            pythonCode = """import urllib.request, json
query = "{{INPUT}}"
url = f"https://picsum.photos/v2/list?page=1&limit=8"
data = json.loads(urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"}), timeout=10).read().decode())
print(f"Image Results for '{query}':\n")
for i, img in enumerate(data[:8], 1):
    print(f"  {i}. {img['author']}")
    print(f"     URL: {img['download_url']}")
    print(f"     Size: {img['width']}x{img['height']}")
    print()"""
        ),
        Tool(
            id = "wikipedia",
            name = "Wikipedia",
            description = "Search Wikipedia for information",
            icon = "wikipedia.org",
            category = "Search",
            pythonCode = """import urllib.request, urllib.parse, json
query = "{{INPUT}}"
encoded = urllib.parse.quote(query)
search_url = f"https://en.wikipedia.org/api/rest_v1/page/summary/{encoded}"
try:
    data = json.loads(urllib.request.urlopen(urllib.request.Request(search_url, headers={"User-Agent": "Mozilla/5.0"}), timeout=10).read().decode())
    print(f"Wikipedia: {data.get('title','')}\n")
    print(data.get('extract', 'No content found.'))
    if data.get('content_urls'):
        print(f"\nRead more: {data['content_urls']['desktop']['page']}")
except:
    search_url2 = f"https://en.wikipedia.org/w/api.php?action=opensearch&search={encoded}&limit=5&format=json"
    results = json.loads(urllib.request.urlopen(urllib.request.Request(search_url2, headers={"User-Agent": "Mozilla/5.0"}), timeout=10).read().decode())
    print(f"Wikipedia search results for '{query}':")
    for title, desc, url in zip(results[1], results[2], results[3]):
        print(f"\n  {title}")
        if desc: print(f"  {desc[:100]}")
        print(f"  {url}")"""
        ),
        Tool(
            id = "translator",
            name = "Translator",
            description = "Translate text between languages",
            icon = "translate.google.com",
            category = "Utility",
            pythonCode = """import urllib.request, urllib.parse, json
input_text = "{{INPUT}}"
parts = input_text.split(" to ", 1)
text = parts[0].strip()
target = parts[1].strip() if len(parts) > 1 else "hi"
lang_map = {"hindi":"hi","spanish":"es","french":"fr","german":"de","japanese":"ja","chinese":"zh","korean":"ko","arabic":"ar","russian":"ru","portuguese":"pt","italian":"it","dutch":"nl","turkish":"tr","thai":"th","bengali":"bn","urdu":"ur"}
target_code = lang_map.get(target.lower(), target[:2].lower())
url = f"https://api.mymemory.translated.net/get?q={urllib.parse.quote(text)}&langpair=en|{target_code}"
data = json.loads(urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"}), timeout=10).read().decode())
translated = data['responseData']['translatedText']
print(f"Translation (en -> {target_code}):\n")
print(f"  Original: {text}")
print(f"  Translated: {translated}")"""
        ),
        Tool(
            id = "dictionary",
            name = "Dictionary",
            description = "Get word definitions and examples",
            icon = "dictionary",
            category = "Utility",
            pythonCode = """import urllib.request, json
word = "{{INPUT}}".strip().lower()
url = f"https://api.dictionaryapi.dev/api/v2/entries/en/{word}"
data = json.loads(urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"}), timeout=10).read().decode())
entry = data[0]
print(f"Dictionary: {entry['word']}")
if entry.get('phonetic'): print(f"Pronunciation: {entry['phonetic']}")
print()
for meaning in entry.get('meanings', [])[:3]:
    print(f"  [{meaning['partOfSpeech']}]")
    for defn in meaning.get('definitions', [])[:2]:
        print(f"    - {defn['definition']}")
        if defn.get('example'): print(f"      Example: \"{defn['example']}\"")
    print()"""
        ),
        Tool(
            id = "ip_geo",
            name = "IP Lookup",
            description = "Get IP address and geolocation info",
            icon = "location",
            category = "Utility",
            pythonCode = """import urllib.request, json
query = "{{INPUT}}".strip()
url = f"http://ip-api.com/json/{query}" if query and query != "my ip" else "http://ip-api.com/json/"
data = json.loads(urllib.request.urlopen(urllib.request.Request(url), timeout=10).read().decode())
print(f"IP Geolocation:")
print(f"  IP: {data.get('query','N/A')}")
print(f"  City: {data.get('city','N/A')}")
print(f"  Region: {data.get('regionName','N/A')}")
print(f"  Country: {data.get('country','N/A')}")
print(f"  ZIP: {data.get('zip','N/A')}")
print(f"  Lat/Lon: {data.get('lat','')}, {data.get('lon','')}")
print(f"  Timezone: {data.get('timezone','N/A')}")
print(f"  ISP: {data.get('isp','N/A')}")
print(f"  Org: {data.get('org','N/A')}")"""
        ),
        Tool(
            id = "qr_code",
            name = "QR Code",
            description = "Generate QR code for any text or URL",
            icon = "qr",
            category = "Utility",
            pythonCode = """import urllib.parse
text = "{{INPUT}}"
encoded = urllib.parse.quote(text)
qr_url = f"https://api.qrserver.com/v1/create-qr-code/?size=300x300&data={encoded}"
print(f"QR Code Generated!")
print(f"  Content: {text}")
print(f"  Image URL: {qr_url}")
print(f"\n  Download: {qr_url}")
print(f"\n  You can view this QR code image at the URL above.")"""
        ),
        Tool(
            id = "math_solver",
            name = "Math Solver",
            description = "Solve equations and math problems",
            icon = "math",
            category = "Utility",
            pythonCode = """import subprocess, sys
subprocess.run([sys.executable, '-m', 'pip', 'install', 'sympy', '--quiet', '--target', '/tmp/pylibs'], capture_output=True, timeout=45)
sys.path.insert(0, '/tmp/pylibs')
from sympy import *
x, y, z = symbols('x y z')
problem = "{{INPUT}}"
print(f"Math Solver: {problem}\n")
try:
    result = sympify(problem)
    print(f"  Result: {result}")
    simplified = simplify(result)
    if simplified != result: print(f"  Simplified: {simplified}")
except:
    try:
        expr = sympify(problem.replace('=','-(') + ')')
        solutions = solve(expr, x)
        print(f"  Solutions: {solutions}")
    except Exception as e:
        print(f"  Could not solve: {e}")
        print(f"  Trying eval: {eval(problem)}")"""
        ),
        Tool(
            id = "color_palette",
            name = "Color Palette",
            description = "Generate color palettes from keywords",
            icon = "palette",
            category = "Utility",
            pythonCode = """import hashlib, colorsys
theme = "{{INPUT}}"
print(f"Color Palette for: {theme}\n")
palettes = []
for i in range(6):
    seed = f"{theme}_{i}"
    h = hashlib.sha256(seed.encode()).hexdigest()
    r, g, b = int(h[:2],16), int(h[2:4],16), int(h[4:6],16)
    hex_color = f"#{r:02x}{g:02x}{b:02x}"
    hue, sat, val = colorsys.rgb_to_hsv(r/255, g/255, b/255)
    palettes.append((hex_color, r, g, b))
    print(f"  {hex_color}  RGB({r},{g},{b})  HSV({hue:.0%},{sat:.0%},{val:.0%})")
print(f"\nCSS: background: linear-gradient(135deg, {palettes[0][0]}, {palettes[2][0]}, {palettes[4][0]});")"""
        ),
        Tool(
            id = "github_trending",
            name = "GitHub Trending",
            description = "Get trending repositories on GitHub",
            icon = "github.com",
            category = "Dev",
            pythonCode = """import urllib.request, json
query = "{{INPUT}}".strip()
if query and query != "trending":
    url = f"https://api.github.com/search/repositories?q={query}&sort=stars&order=desc&per_page=8"
else:
    url = "https://api.github.com/search/repositories?q=stars:>50000&sort=stars&order=desc&per_page=10"
data = json.loads(urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "AhamAI/1.0"}), timeout=10).read().decode())
print(f"GitHub {'Search: '+query if query else 'Trending'}:\n")
for r in data.get('items', [])[:10]:
    stars = r['stargazers_count']
    print(f"  {r['full_name']} ★{stars:,}")
    desc = r.get('description','')
    if desc: print(f"    {desc[:70]}")
    print(f"    Lang: {r.get('language','?')} | Forks: {r['forks_count']:,}")
    print()"""
        ),
        Tool(
            id = "url_expander",
            name = "URL Inspector",
            description = "Expand short URLs and get page info",
            icon = "link",
            category = "Utility",
            pythonCode = """import subprocess, sys
subprocess.run([sys.executable, '-m', 'pip', 'install', 'beautifulsoup4', '--quiet', '--target', '/tmp/pylibs'], capture_output=True, timeout=30)
sys.path.insert(0, '/tmp/pylibs')
import urllib.request
from bs4 import BeautifulSoup
url = "{{INPUT}}"
if not url.startswith('http'): url = 'https://' + url
req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"})
resp = urllib.request.urlopen(req, timeout=10)
final_url = resp.url
html = resp.read().decode("utf-8", errors="ignore")
soup = BeautifulSoup(html, 'html.parser')
title = soup.title.string.strip() if soup.title else "No title"
desc_tag = soup.find('meta', attrs={'name':'description'}) or soup.find('meta', attrs={'property':'og:description'})
desc = desc_tag['content'][:200] if desc_tag and desc_tag.get('content') else ""
print(f"URL Inspector:")
print(f"  Input: {url}")
print(f"  Final URL: {final_url}")
print(f"  Title: {title}")
if desc: print(f"  Description: {desc}")
print(f"  Size: {len(html):,} bytes")"""
        ),
    )

    fun getToolById(id: String): Tool? = ALL_TOOLS.find { it.id == id }

    fun getToolsByCategory(category: String): List<Tool> = ALL_TOOLS.filter { it.category == category }

    /**
     * Prepares Python code for execution by replacing {{INPUT}} placeholder.
     */
    fun prepareCode(tool: Tool, input: String): String {
        return tool.pythonCode.replace("{{INPUT}}", input.replace("\"", "\\\""))
    }
}
