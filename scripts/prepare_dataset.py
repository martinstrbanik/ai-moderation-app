#!/usr/bin/env python3
"""
Príprava datasetu Davidson et al. (2017) — Hate Speech and Offensive Language
pre benchmark modul aplikácie.

Vstup:  data/raw/labeled_data.csv   (originálny CSV z GitHub repozitára
        t-davidson/hate-speech-and-offensive-language, stĺpce:
        count, hate_speech, offensive_language, neither, class, tweet)

Výstup (data/processed/davidson/):
  labeled_data.jsonl  — celý dataset, jeden záznam na riadok
  extra_light.jsonl   — vyvážená podvzorka (50 vzoriek / triedu = 150)
  light.jsonl         — vyvážená podvzorka (500 vzoriek / triedu = 1500)
  meta.json           — štatistiky a metadáta

Formát záznamu (JSONL):
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

# class -> (label_id, meno)
CLASS_LABELS = {0: "hate_speech", 1: "offensive", 2: "neither"}

SUBSETS = {
    "extra_light": 50,   # 50 vzoriek na triedu => 150 spolu
    "light": 500,        # 500 vzoriek na triedu => 1500 spolu
}


def normalize_text(raw: str) -> str:
    """Unescape HTML entít (CSV je XML-escaped) a odstrihnúť biele znaky."""
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
    """Vyvážený výber: rovnako vzoriek z každej triedy (kvôli metrikám)."""
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

    # Usporiadanie náhodne (deterministické) pre celý dataset
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

    print(f"Hotovo. Celkovo vzoriek: {len(samples)}")
    print("Rozdelenie tried (celé):")
    for label in CLASS_LABELS.values():
        print(f"  {label}: {stats['class_counts'][label]}")
    for name in SUBSETS:
        info = stats["subsets"][name]
        print(f"{name}: {info['total']} vzoriek -> {dict(info['class_counts'])}")


if __name__ == "__main__":
    main()
