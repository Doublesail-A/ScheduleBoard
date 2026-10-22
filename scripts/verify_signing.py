"""Pin the existing installation's certificate so CI cannot break upgrades/OAuth."""
from pathlib import Path
import re
import sys

EXPECTED = "f7a1eb56d6148724d85cf4e3147276e34442cdcb8ee2a4842240b71a08eafdc8"
text = Path(sys.argv[1]).read_text(encoding="utf-8")
certificates = re.findall(r"Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F]+)", text)
if certificates != [EXPECTED]:
    raise SystemExit("Unexpected APK signing identity; refusing to publish an incompatible update")
print("Fixed distribution signing certificate verified.")
