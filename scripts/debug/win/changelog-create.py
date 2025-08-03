import requests
#
# Command line.
## python -m ensurepip --upgrade
## python -m pip install --upgrade pip
## "%AppData%\Python\Python313\Scripts\pip3.exe" install requests
## python changelog-create.py
#
# Consts.
REPO = "Catfriend1/syncthing-android"
TOKEN = "GHP_TOKEN"
API_URL = f"https://api.github.com/repos/{REPO}/releases"
#
headers = {
    "Authorization": f"token {TOKEN}",
    "Accept": "application/vnd.github.v3+json"
}
#
releases = []
page = 1
#
while True:
    url = f"https://api.github.com/repos/{REPO}/releases?page={page}&per_page=100"
    response = requests.get(url, headers=headers)
    data = response.json()
    if not data:
        break
    releases.extend(data)
    page += 1
#
with open("CHANGELOG.md", "w", encoding="utf-8") as f:
    for release in releases:
        name = release.get("name") or release.get("tag_name")
        date = release.get("published_at", "")[:10]
        body = release.get("body", "").strip().replace("\r\n", "\n").replace("\n\n", "\n")
        f.write(f"## {name} ({date})\n{body}\n\n---\n\n")
#
print("[INFO] Done.")
