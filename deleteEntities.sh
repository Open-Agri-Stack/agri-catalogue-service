#!/usr/bin/env bash
set -euo pipefail

entities=(
  "seed"
  "cropType"
  "cropVariety"
  "cropCategory"
  "livestock"
  "livestockBreed"
  "livestockCategory"
  "season"
  "soil"
  "extensionequipment"
  "pesticide"
  "insecticide"
  "fertilizer"
  "locationObject"
  "locationMapper"
  "locationConfig"
  "marketPlace"
)

for entity in "${entities[@]}"; do
  echo "Deleting entity: $entity"
  python3 main.py --action delete --name "$entity"
done

# seed, cropType, cropVariety, cropCategory, livestock, livestockBreed, livestockCategory, season, soil, extensionequipment, pesticide, insecticide, fertilizer, locationObject, locationMapper, locationConfig, marketPlace
#
# Note: the "audit" catalogue is also generated from these templates but is deliberately
# NOT listed here - its service impl is hand-maintained (AuditService backs AuditLogService),
# so it must not be swept away by this script.
