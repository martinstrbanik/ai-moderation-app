#!/usr/bin/env python3
"""
Preparation of the TUKE-KEMT/hate_speech_slovak dataset (Slovak hate speech,
binary 0/1) for the application benchmark module.

Input:  data/raw/tuke_slovak/train.json
        data/raw/tuke_slovak/test.json
        (JSON-lines: {"id": int, "text": str, "label": int}; label 0 = not_hate, 1 = hate)

Output (data/processed/tuke_slovak/):
  labeled_data.jsonl  - full dataset, one record per line
  extra_light.jsonl   - balanced subsample (50 samples per class = 100)
  light.jsonl         - balanced subsample (500 samples per class = 1000)
  meta.json           - statistics and metadata

Record format (JSONL):
  {"id": <int>, "text": <str>, "label": <str>, "label_id": <int>}
"""

import argparse
import json
import random
from collections import Counter
from datetime import date
from pathlib import Path

RAW_DIR = Path("data/raw/tuke_slovak")
TRAIN_PATH = RAW_DIR / "train.json"
TEST_PATH = RAW_DIR / "test.json"
OUT_DIR = Path("data/processed/tuke_slovak")

# label int -> (label_id, name)
LABELS = {0: "not_hate", 1: "hate"}

SUBSETS = {
    "extra_light": 50,   # 50 samples per class => 100 total
    "light": 500,        # 500 samples per class => 1000 total
}


def read_jsonl(path: Path):
    samples = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            row = json.loads(line)
            label_id = int(row["label"])
            text = (row.get("text") or "").strip()
            if not text:
                continue
            samples.append(
                {"id": int(row["id"]), "text": text,
                 "label": LABELS[label_id], "label_id": label_id}
            )
    return samples


def write_jsonl(samples: list, path: Path) -> None:
    with open(path, "w", encoding="utf-8") as f:
        for s in samples:
            f.write(json.dumps(s, ensure_ascii=False) + "\n")


def sample_balanced(samples: list, per_class: int, rng: random.Random) -> list:
    """Balanced sampling: the same number of samples from each class (for metrics)."""
    by_label = {}
    for s in samples:
        by_label.setdefault(s["label_id"], []).append(s)
    chosen = []
    for label_id, items in by_label.items():
        chosen.extend(rng.sample(items, min(per_class, len(items))))
    rng.shuffle(chosen)
    return chosen


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    rng = random.Random(args.seed)
    samples = read_jsonl(TRAIN_PATH) + read_jsonl(TEST_PATH)

    rng.shuffle(samples)

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    write_jsonl(samples, OUT_DIR / "labeled_data.jsonl")

    stats = {
        "source": "TUKE-KEMT/hate_speech_slovak (Slovak hate speech, binary)",
        "source_url": "https://huggingface.co/datasets/TUKE-KEMT/hate_speech_slovak",
        "language": "sk",
        "created": str(date.today()),
        "seed": args.seed,
        "total": len(samples),
        "class_counts": Counter(s["label"] for s in samples),
        "class_labels": LABELS,
        "subsets": {},
    }

    for name, per_class in SUBSETS.items():
        subset = sample_balanced(samples, per_class, rng)
        write_jsonl(subset, OUT_DIR / f"{name}.jsonl")
        stats["subsets"][name] = {
            "per_class": per_class,
            "total": len(subset),
            "class_counts": Counter(s["label"] for s in subset),
        }

    with open(OUT_DIR / "meta.json", "w", encoding="utf-8") as f:
        json.dump(stats, f, ensure_ascii=False, indent=2)

    print(f"Done. Total samples: {len(samples)}")
    print("Class distribution (full):")
    for label_id in sorted(LABELS):
        print(f"  {LABELS[label_id]}: {stats['class_counts'][LABELS[label_id]]}")
    for name in SUBSETS:
        info = stats["subsets"][name]
        print(f"{name}: {info['total']} samples -> {dict(info['class_counts'])}")


if __name__ == "__main__":
    main()