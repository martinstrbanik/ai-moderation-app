# Datasety pre benchmark

## Davidson et al. (2017) — Hate Speech and Offensive Language

**Zdroj:** Twitter príspevky anotované na 3 triedy.
- `0` = `hate_speech`
- `1` = `offensive`
- `2` = `neither`

**Citácia a licencia:**
> Davidson, T., Warmsley, D., Macy, M., & Weber, I. (2017). *Automated Hate Speech Detection
> and the Problem of Offensive Language.* Proceedings of ICWSM '17.
> https://github.com/t-davidson/hate-speech-and-offensive-language

Dataset bol stiahnutý z verejného GitHub repozitára autorov (súbor `data/labeled_data.csv`) na
akademické použitie. Pri citovaní v práci je potrebné uviesť pôvodný zdroj vyššie.

## Štruktúra

```
data/raw/labeled_data.csv              -> originálny CSV (neupravovaný)
data/processed/davidson/               -> spracované verzie (JSONL)
  labeled_data.jsonl                   -> celý dataset
  extra_light.jsonl                    -> vyvážená podvzorka 150 (50/trieda)
  light.jsonl                          -> vyvážená podvzorka 1500 (500/trieda)
  meta.json                            -> štatistiky a metadáta
scripts/prepare_dataset.py             -> skript na prípravu
```

### Prečo vyvážené podvzorky (extra_light / light)

Originálny dataset je nevyvážený (77 % „offensive“). Pri malých vzorkách by v proporcionálnom
výbere nebolo dostatok príkladov na výpočet zmysluplných metrík (precision/recall/F1) pre triedu
`hate_speech`. Podvzorky preto vyberáme **vyvážene** (rovnaký počet z každej triedy), zatiaľ čo
`labeled_data.jsonl` (celý dataset) zachováva pôvodné rozdelenie pre „FULL“ behy.

## Reprodukovateľnosť

Výber vzoriek je deterministický (pevný `seed = 42`). Prepočítať je možné cez:

```bash
python3 scripts/prepare_dataset.py
```