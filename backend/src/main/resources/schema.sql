-- Auth
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    better_password VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS sessions (
    token VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON sessions (user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_expires_at ON sessions (expires_at);

-- Ephemeris reference epoch
CREATE TABLE IF NOT EXISTS ephemeris_reference (
    id              INTEGER PRIMARY KEY CHECK (id = 1),
    epoch_jd        DOUBLE PRECISION NOT NULL,
    frame           VARCHAR(32) NOT NULL,
    plane           VARCHAR(32) NOT NULL,
    time_scale      VARCHAR(16) NOT NULL,
    source          VARCHAR(128) NOT NULL
);

-- Ephemeris bodies as tree of conics.
CREATE TABLE IF NOT EXISTS celestial_bodies (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL UNIQUE,

    type            VARCHAR(32) NOT NULL
                    CHECK (type IN (
                        'star', 'planet', 'moon', 'dwarf_planet',
                        'asteroid', 'spacecraft', 'other'
                    )),

    -- Solar system tree
    parent_id       BIGINT NULL REFERENCES celestial_bodies (id),

    -- Attractor and size.
    mu              DOUBLE PRECISION NOT NULL CHECK (mu > 0),
    radius          DOUBLE PRECISION NULL CHECK (radius IS NULL OR radius >= 0),

    -- IAU body equator and prime meridian (NAIF pck00011), radians.
    -- Pole rates are per Julian century; prime-meridian rates are per day.
    -- TDB time argument is days from J2000. NULL if the
    -- kernel has no orientation for that body.
    pole_ra_0           DOUBLE PRECISION NULL,
    pole_ra_1           DOUBLE PRECISION NULL,
    pole_ra_2           DOUBLE PRECISION NULL,
    pole_dec_0          DOUBLE PRECISION NULL,
    pole_dec_1          DOUBLE PRECISION NULL,
    pole_dec_2          DOUBLE PRECISION NULL,
    prime_meridian_0    DOUBLE PRECISION NULL,
    prime_meridian_1    DOUBLE PRECISION NULL,
    prime_meridian_2    DOUBLE PRECISION NULL
);

CREATE INDEX IF NOT EXISTS idx_celestial_bodies_parent_id
    ON celestial_bodies (parent_id);
CREATE INDEX IF NOT EXISTS idx_celestial_bodies_type
    ON celestial_bodies (type);

-- Celestial body fact sheet.
CREATE TABLE IF NOT EXISTS celestial_statistics (
    celestial_body_id   BIGINT PRIMARY KEY
                        REFERENCES celestial_bodies (id) ON DELETE CASCADE,
    spectral_class      VARCHAR(32) NULL,
    temperature         DOUBLE PRECISION NULL CHECK (temperature IS NULL OR temperature >= 0),
    pressure            DOUBLE PRECISION NULL CHECK (pressure IS NULL OR pressure >= 0)
);

-- Osculating Kepler elements about parent. Negative when hyperbolic.
-- epoch is seconds relative to ephemeris_reference.
CREATE TABLE IF NOT EXISTS orbits (
    celestial_body_id     BIGINT PRIMARY KEY
                          REFERENCES celestial_bodies (id) ON DELETE CASCADE,
    semi_major_axis       DOUBLE PRECISION NOT NULL,
    eccentricity          DOUBLE PRECISION NOT NULL CHECK (eccentricity >= 0),
    inclination           DOUBLE PRECISION NOT NULL DEFAULT 0,
    long_asc_node         DOUBLE PRECISION NOT NULL DEFAULT 0,
    arg_periapsis         DOUBLE PRECISION NOT NULL DEFAULT 0,
    mean_anomaly_at_epoch DOUBLE PRECISION NOT NULL DEFAULT 0,
    epoch                 DOUBLE PRECISION NOT NULL DEFAULT 0
);
