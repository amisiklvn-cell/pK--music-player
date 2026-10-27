from pathlib import Path
import shutil

p = Path("app/src/main/java/com/rekans/pk/MainActivity.java")
s = p.read_text()

def check(old, label):
    if s.count(old) != 1:
        raise SystemExit(f"STOP: Could not uniquely locate {label}. No source changes made.")

queue_block = '''        LinearLayout extraActions = new LinearLayout(this);
        extraActions.setGravity(Gravity.CENTER);
        extraActions.setOrientation(LinearLayout.HORIZONTAL);

        queueButton = makeButton("☷ Queue");
        lyricsButton = makeButton("♫ Lyrics");

        extraActions.addView(queueButton);
        extraActions.addView(lyricsButton);

        queueButton.setOnClickListener(v -> showQueue());
        lyricsButton.setOnClickListener(v -> showLyrics());
'''

layout_old = '''        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(nowPanel);'''

check("    private Button queueButton;\n", "Queue field")
check("    private Button lyricsButton;\n", "Lyrics field")
check(queue_block, "Queue/Lyrics button block")
check("        nowPanel.addView(extraActions);\n", "extraActions attachment")
check("new LinearLayout.LayoutParams(dp(150), dp(150));", "artwork size")
check(layout_old, "player layout")
check("    private void showQueue() {", "showQueue method")
check("    private void showLyrics() {", "showLyrics method")

def remove_method(source, signature):
    start = source.index(signature)
    brace = source.index("{", start)
    depth = 0
    for i in range(brace, len(source)):
        if source[i] == "{":
            depth += 1
        elif source[i] == "}":
            depth -= 1
            if depth == 0:
                end = i + 1
                while end < len(source) and source[end] == "\n":
                    end += 1
                return source[:start] + source[end:]
    raise SystemExit(f"STOP: Could not find closing brace for {signature.strip()}.")

# Make all edits in memory first.
s = s.replace("    private Button queueButton;\n", "")
s = s.replace("    private Button lyricsButton;\n", "")
s = s.replace(queue_block, "")
s = s.replace("        nowPanel.addView(extraActions);\n", "")
s = s.replace(
    "new LinearLayout.LayoutParams(dp(150), dp(150));",
    "new LinearLayout.LayoutParams(dp(180), dp(180));"
)
s = s.replace(
    layout_old,
    '''        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(100)));
        root.addView(nowPanel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));'''
)

s = remove_method(s, "    private void showQueue() {")
s = remove_method(s, "    private void showLyrics() {")

for term in ("queueButton", "lyricsButton", "showQueue()", "showLyrics()", "extraActions"):
    if term in s:
        raise SystemExit(f"STOP: Reference remains: {term}. No source changes made.")

# Keep the existing backup and create a fresh timestamp-independent copy.
backup = Path.home() / "pk-backups" / "MainActivity.before-queue-lyrics-removal.java"
if not backup.exists():
    shutil.copy2(p, backup)

p.write_text(s)
print("SUCCESS: Queue and Lyrics UI/dialogs removed.")
print("SUCCESS: Artwork placeholder enlarged to 180dp.")
print("SUCCESS: Player receives the remaining vertical space.")
print("Backup:", backup)
