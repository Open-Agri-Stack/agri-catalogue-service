#!/usr/bin/env bash
set -euo pipefail

entities=(
  "seed"
  "cropType"
  "cropCategory"
  "cropVariety"
  "livestock"
  "season"
  "soil"
  "extensionequipment"
  "pesticide"
  "insecticide"
  "fertilizer"
  "location"
)

for entity in "${entities[@]}"; do
  echo "Creating entity: $entity"
  python3 main.py --action delete --name "$entity"
done
