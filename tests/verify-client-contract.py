from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'app/src/main/java/id/franzz/orbit'
BOT = (SRC / 'BotOverlay.kt').read_text(encoding='utf-8')
API = (SRC / 'Api.kt').read_text(encoding='utf-8')
MAIN = (SRC / 'MainActivity.kt').read_text(encoding='utf-8')
SCREEN = (SRC / 'ScreenReader.kt').read_text(encoding='utf-8')
MANIFEST = ROOT / 'app/src/main/AndroidManifest.xml'
BUILD = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')
WORKFLOW = (ROOT / '.github/workflows/build-apk.yml').read_text(encoding='utf-8')

root = ET.parse(MANIFEST).getroot()
android_ns = '{http://schemas.android.com/apk/res/android}'
app = root.find('application')
assert app is not None
icon = app.attrib.get(android_ns + 'icon')
assert icon == '@mipmap/ic_launcher', f'Incorrect launcher icon: {icon}'
assert (ROOT / 'app/src/main/res/mipmap-mdpi/ic_launcher.png').is_file()
assert 'compileSdk = 35' in BUILD
assert 'DEFAULT_SERVER' in BUILD and 'https://www.franzzorbit.my.id' in BUILD
assert 'gradle-version: \'8.9\'' in WORKFLOW
client_sources = API + MAIN + BOT
for endpoint in ['/api/status', '/api/login', '/api/chat', '/api/analyze', '/api/gemini', '/api/study']:
    assert endpoint in client_sources, f'Missing endpoint in Android client: {endpoint}'
for label in ['Scan & Fill', 'Analisis Halaman AI', 'Fitur AI', 'Chat AI', 'Tutor AI', 'Tanya AI', 'Penjelasan lengkap', 'Ringkas Materi', 'Cek Jawaban', 'Latihan Soal']:
    assert label in BOT, f'Missing tool in popup: {label}'
assert 'node.isPassword' in SCREEN, 'Password fields must be excluded from screen reader.'
assert 'panelReady' in MAIN, 'Status should check Firestore panel readiness.'
assert re.search(r'id\("com\.android\.application"\) version "8\.7\.2"', (ROOT / 'build.gradle.kts').read_text())
assert re.search(r'id\("org\.jetbrains\.kotlin\.android"\) version "2\.1\.20"', (ROOT / 'build.gradle.kts').read_text())
print('TES KONTRAK KLIEN ANDROID/WEB LULUS (statis; bukan build Gradle)')
