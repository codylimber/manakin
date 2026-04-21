#!/usr/bin/env python3
"""
Generate the bundled CT Butterflies dataset for the Manakin app.
Mirrors the Kotlin DatasetGenerator logic with observation photo fallback.

Usage: python3 scripts/generate_bundle.py
"""

import json
import os
import re
import time
import urllib.request
import urllib.parse
from datetime import datetime, timezone

BASE_URL = "https://api.inaturalist.org/v1"
USER_AGENT = "Manakin/1.2 (Android; github.com/codylimber/manakin)"
RATE_LIMIT = 1.0  # seconds between requests

# Config
PLACE_ID = 49  # Connecticut
TAXON_ID = 47224  # Papilionoidea (Butterflies)
MIN_OBS = 10
QUALITY_GRADE = "research"
MAX_PHOTOS = 3
GROUP_NAME = "Butterflies"
PLACE_NAME = "Connecticut, US"
TAXON_NAME = "Butterflies (Papilionoidea)"

OUTPUT_DIR = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "datasets", "butterflies")

last_request = 0

def api_get(endpoint, params=None):
    global last_request
    elapsed = time.time() - last_request
    if elapsed < RATE_LIMIT:
        time.sleep(RATE_LIMIT - elapsed)

    url = f"{BASE_URL}/{endpoint}"
    if params:
        url += "?" + urllib.parse.urlencode(params)

    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    for attempt in range(5):
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                last_request = time.time()
                return json.loads(resp.read())
        except Exception as e:
            if attempt < 4:
                time.sleep(5 * (2 ** attempt))
                continue
            raise

def download_photo(url):
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.read()
    except Exception:
        return None

def main():
    photos_dir = os.path.join(OUTPUT_DIR, "photos")
    os.makedirs(photos_dir, exist_ok=True)

    # Step 1: Fetch species list
    print("Fetching species list...")
    species_raw = []
    obs_counts = {}
    page = 1
    while True:
        data = api_get("observations/species_counts", {
            "taxon_id": TAXON_ID,
            "place_id": PLACE_ID,
            "quality_grade": QUALITY_GRADE,
            "page": page,
            "per_page": 200
        })
        results = data.get("results", [])
        for r in results:
            taxon = r.get("taxon", {})
            tid = taxon.get("id")
            count = r.get("count", 0)
            if tid and count >= MIN_OBS:
                species_raw.append(r)
                obs_counts[tid] = count
        print(f"  Page {page}: {len(results)} results, {len(species_raw)} species >= {MIN_OBS} obs")
        if len(results) < 200:
            break
        page += 1

    print(f"Found {len(species_raw)} species")

    # Build species list with rarity
    max_obs = max(obs_counts.values()) if obs_counts else 1
    species_list = []
    for r in species_raw:
        taxon = r["taxon"]
        tid = taxon["id"]
        count = obs_counts[tid]
        ratio = count / max_obs
        rarity = "Common" if ratio >= 0.05 else ("Uncommon" if ratio >= 0.005 else "Rare")
        species_list.append({
            "taxonId": tid,
            "scientificName": taxon.get("name", ""),
            "commonName": taxon.get("preferred_common_name", ""),
            "totalObs": count,
            "rarity": rarity,
            "peakWeek": 1,
            "firstWeek": 1,
            "lastWeek": 53,
            "periodCount": 1,
        })

    # Step 2: Fetch histograms
    print("Fetching histograms...")
    for i, sp in enumerate(species_list):
        print(f"  Histogram {i+1}/{len(species_list)}: {sp['commonName'] or sp['scientificName']}")
        data = api_get("observations/histogram", {
            "taxon_id": sp["taxonId"],
            "place_id": PLACE_ID,
            "quality_grade": QUALITY_GRADE,
            "date_field": "observed",
            "interval": "week_of_year"
        })
        week_data = data.get("results", {}).get("week_of_year", {})
        histogram = {int(k): v for k, v in week_data.items() if 1 <= int(k) <= 53}

        active = {k: v for k, v in histogram.items() if v > 0}
        if active:
            sp["peakWeek"] = max(active, key=active.get)
            sp["firstWeek"] = min(active.keys())
            sp["lastWeek"] = max(active.keys())

        # Build weekly entries
        total_obs_weekly = sum(histogram.values()) or 1
        peak_n = max(histogram.values()) if histogram else 1
        weekly = []
        for w in range(1, 54):
            n = histogram.get(w, 0)
            rel = n / peak_n if peak_n > 0 else 0.0
            weekly.append({"week": w, "n": n, "relAbundance": round(rel, 4)})
        sp["weekly"] = weekly

        # Detect flight periods (gaps of 3+ zero weeks)
        active_weeks = sorted(active.keys())
        periods = 1
        for j in range(1, len(active_weeks)):
            if active_weeks[j] - active_weeks[j-1] > 3:
                periods += 1
        sp["periodCount"] = periods if active_weeks else 1

    # Step 3: Fetch taxa details
    print("Fetching species details...")
    taxa_details = {}
    all_ids = [sp["taxonId"] for sp in species_list]
    for i in range(0, len(all_ids), 30):
        batch = all_ids[i:i+30]
        ids_str = ",".join(str(x) for x in batch)
        print(f"  Details batch {i+1}-{min(i+30, len(all_ids))}/{len(all_ids)}")
        data = api_get(f"taxa/{ids_str}")
        for t in data.get("results", []):
            taxa_details[t["id"]] = t

    def get_ancestor(taxon_info, rank):
        for a in taxon_info.get("ancestors", []):
            if a.get("rank") == rank:
                return a.get("preferred_common_name") or a.get("name"), a.get("name")
        return None, None

    for sp in species_list:
        info = taxa_details.get(sp["taxonId"], {})
        family_common, family_sci = get_ancestor(info, "family")
        order_common, order_sci = get_ancestor(info, "order")
        sp["family"] = family_common
        sp["familyScientific"] = family_sci
        sp["order"] = order_common
        sp["orderScientific"] = order_sci

        conservation = info.get("conservation_status")
        if conservation and isinstance(conservation, dict):
            sp["conservationStatus"] = conservation.get("status")
            sp["conservationStatusName"] = conservation.get("status_name")
        else:
            sp["conservationStatus"] = None
            sp["conservationStatusName"] = None

        desc = info.get("wikipedia_summary") or ""
        sp["description"] = re.sub(r"<[^>]+>", "", desc).strip()

    # Step 4: Download photos
    print("Downloading photos...")
    for i, sp in enumerate(species_list):
        print(f"  Photos {i+1}/{len(species_list)}: {sp['commonName'] or sp['scientificName']}")
        info = taxa_details.get(sp["taxonId"], {})

        # Extract taxon photos
        cc_photos = []
        taxon_photos = info.get("taxon_photos", [])
        for tp in taxon_photos:
            photo = tp.get("photo", {})
            license = photo.get("license_code", "")
            if license and license.startswith("cc"):
                cc_photos.append(photo)

        if not cc_photos:
            default = info.get("default_photo", {})
            license = default.get("license_code", "")
            if license and license.startswith("cc"):
                cc_photos.append(default)

        # Fallback to observation photos if insufficient
        if len(cc_photos) < MAX_PHOTOS:
            try:
                obs_data = api_get("observations", {
                    "taxon_id": sp["taxonId"],
                    "place_id": PLACE_ID,
                    "quality_grade": QUALITY_GRADE,
                    "photos": "true",
                    "photo_licensed": "true",
                    "per_page": str(MAX_PHOTOS - len(cc_photos)),
                    "order_by": "votes"
                })
                for obs in obs_data.get("results", []):
                    for photo in obs.get("photos", []):
                        license = photo.get("license_code", "")
                        if license and license.startswith("cc"):
                            cc_photos.append(photo)
            except Exception:
                pass

        sp_photos = []
        for pi, photo in enumerate(cc_photos[:MAX_PHOTOS]):
            url = photo.get("medium_url") or photo.get("url", "")
            url = url.replace("/square.", "/medium.")
            if not url:
                continue

            filename = f"{sp['taxonId']}_{pi}.jpg"
            img_bytes = download_photo(url)
            if img_bytes:
                with open(os.path.join(photos_dir, filename), "wb") as f:
                    f.write(img_bytes)
                sp_photos.append({
                    "file": filename,
                    "attribution": photo.get("attribution"),
                    "license": photo.get("license_code")
                })
        sp["photos"] = sp_photos

    # Step 5: Build and save dataset
    print("Saving dataset...")
    dataset = {
        "metadata": {
            "placeName": PLACE_NAME,
            "placeId": PLACE_ID,
            "placeIds": [PLACE_ID],
            "group": GROUP_NAME,
            "taxonName": TAXON_NAME,
            "taxonIds": [TAXON_ID],
            "totalObs": sum(sp["totalObs"] for sp in species_list),
            "speciesCount": len(species_list),
            "generatedAt": datetime.now(timezone.utc).isoformat(),
            "minObs": MIN_OBS,
            "qualityGrade": QUALITY_GRADE,
            "maxPhotos": MAX_PHOTOS
        },
        "species": species_list
    }

    with open(os.path.join(OUTPUT_DIR, "dataset.json"), "w") as f:
        json.dump(dataset, f)

    print(f"Done! {len(species_list)} species saved to {OUTPUT_DIR}")

if __name__ == "__main__":
    main()
