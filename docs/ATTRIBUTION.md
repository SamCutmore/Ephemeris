# Attribution

Credits for data and media under CC BY 4.0 attribution license.

## Sky

**NASA SVS Deep Star Maps 2020**

https://svs.gsfc.nasa.gov/4851/

`frontend/public/starfield/starmap_2020_8k.exr`

- NASA/Goddard Space Flight Center Scientific Visualization Studio
- Gaia DR2: ESA/Gaia/DPAC
- Constellation figures based on those developed for the IAU by Alan MacRobert of *Sky and Telescope* (Roger Sinnott and Rick Fienberg)

```bash
./scripts/fetch/fetch_starfield.sh
```

## Body maps

`frontend/public/starfield/bodies/`

```bash
./scripts/fetch/fetch_planet_textures.sh
```

### Earth

**NASA Blue Marble Next Generation, December 2004**

https://visibleearth.nasa.gov/images/74218/december-blue-marble-next-generation

Credit: NASA Earth Observatory (Reto Stöckli et al.). File: `earth_bluemarble.jpg`.

Tile pyramid from NASA GIBS:

https://gibs.earthdata.nasa.gov/wmts/epsg4326/best/BlueMarble_NextGeneration/default/500m/{z}/{y}/{x}.jpeg

### Solar System Scope

**Solar System Scope 2K textures**

https://www.solarsystemscope.com/textures/

Sun, Mercury, Venus, the Moon, Mars, Jupiter, Saturn, Uranus, and Neptune.

The Earth night texture (`earth_night.png`).

## International Space Station

**NASA ISS model**

https://science.nasa.gov/resource/international-space-station-3d-model/

`frontend/public/starfield/iss/ISS_stationary.glb`

```bash
./scripts/fetch/fetch_iss_model.sh
```

## Catalog

Orbital elements and rotation polynomials located in `backend/src/main/resources/catalog/catalog_seed.sql`.

```bash
./scripts/fetch/fetch_horizons_catalog.py
```

- Osculating elements at J2000.0 TDB, ICRF, from JPL Horizons: https://ssd.jpl.nasa.gov/horizons/
- Pole and prime meridian from the IAU WGCCRE via NAIF `pck00011.tpc`: https://naif.jpl.nasa.gov/pub/naif/generic_kernels/pck/pck00011.tpc
