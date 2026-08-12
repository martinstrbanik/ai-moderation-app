#!/usr/bin/env python3
"""
Preparation of the Davidson et al. (2017) - Hate Speech and Offensive Language
dataset for the application benchmark module.

Input:  data/raw/labeled_data.csv   (original CSV from the GitHub repository
        t-davidson/hate-speech-and-offensive-language, columns:
        count, hate_speech, offensive_language, neither, class, tweet)

Output (data/processed/davidson/):
  labeled_data.jsonl  - full dataset, one record per line
  extra_light.jsonl   - balanced subsample (50 samples per class = 150)
  light.jsonl         - balanced subsample (500 samples per class = 1500)
  meta.json           - statistics and metadata

Record format (JSONL):
  {"id": <int>, "text": <str>, "label": <str>, "label_id": <int>}
"""

import argparse
import csv
import html
import json
import random
from collections import Counter
from datetime import date
from pathlib import Path

RAW_PATH = Path("data/raw/labeled_data.csv")
OUT_DIR = Path("data/processed/davidson")

# class -> (label_id, name)
CLASS_LABELS = {0: "hate_speech", 1: "offensive", 2: "neither"}

SUBSETS = {
    "extra_light": 50,   # 50 samples per class => 150 total
    "light": 500,        # 500 samples per class => 1500 total
}


def normalize_text(raw: str) -> str:
    """Unescape HTML entities (the CSV is XML-escaped) and strip whitespace."""
    return html.unescape(raw).strip()


def read_raw(path: Path):
    samples = []
    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for i, row in enumerate(reader):
            try:
                label_id = int(row["class"])
            except (KeyError, ValueError):
                continue
            text = normalize_text(row.get("tweet", ""))
            if not text:
                continue
            samples.append(
                {"id": i, "text": text, "label": CLASS_LABELS[label_id], "label_id": label_id}
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
    samples = read_raw(RAW_PATH)

    # Shuffle randomly (deterministic) for the whole dataset
    rng.shuffle(samples)

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    write_jsonl(samples, OUT_DIR / "labeled_data.jsonl")

    stats = {
        "source": "Davidson, T., Warmsley, D., Macy, M., & Weber, I. (2017). "
                  "Automated Hate Speech Detection and the Problem of Offensive Language.",
        "source_url": "https://github.com/t-davidson/hate-speech-and-offensive-language",
        "language": "en",
        "created": str(date.today()),
        "seed": args.seed,
        "total": len(samples),
        "class_counts": Counter(s["label"] for s in samples),
        "class_labels": CLASS_LABELS,
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
    for label in CLASS_LABELS.values():
        print(f"  {label}: {stats['class_counts'][label]}")
    for name in SUBSETS:
        info = stats["subsets"][name]
        print(f"{name}: {info['total']} samples -> {dict(info['class_counts'])}")


if __name__ == "__main__":
    main()
