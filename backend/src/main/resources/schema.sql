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
    source          VARCHAR(128) NOT NULL,
    fetched_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Ephemeris bodies as tree of conics.
CREATE TABLE IF NOT EXISTS celestial_bodies (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL UNIQUE,

    kind            VARCHAR(32) NOT NULL
                    CHECK (kind IN (
                        'star', 'planet', 'moon', 'dwarf_planet',
                        'asteroid', 'spacecraft', 'other'
                    )),
    celestial_type  VARCHAR(64) NULL,
    type_id         INT NULL,
    spectral_class  VARCHAR(32) NULL,

    -- Tree
    parent_id       BIGINT NULL REFERENCES celestial_bodies (id),
    celestial_index INT NULL,
    solar_system_id INT NULL,
    orbit_id        BIGINT NULL,

    -- Attractor / size
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
    prime_meridian_2    DOUBLE PRECISION NULL,

    density             DOUBLE PRECISION NULL,
    eccentricity        DOUBLE PRECISION NULL CHECK (eccentricity IS NULL OR eccentricity >= 0),
    escape_velocity     DOUBLE PRECISION NULL,
    mass_dust           DOUBLE PRECISION NULL,
    mass_gas            DOUBLE PRECISION NULL,
    orbit_period        DOUBLE PRECISION NULL,
    orbit_radius        DOUBLE PRECISION NULL,
    pressure            DOUBLE PRECISION NULL,
    rotation_rate       DOUBLE PRECISION NULL,
    surface_gravity     DOUBLE PRECISION NULL,
    temperature         DOUBLE PRECISION NULL,
    locked              BOOLEAN NOT NULL DEFAULT FALSE,
    population          BOOLEAN NOT NULL DEFAULT FALSE,

    height_map_1        INT NULL,
    height_map_2        INT NULL,
    shader_preset       INT NULL,

    position_x          DOUBLE PRECISION NULL,
    position_y          DOUBLE PRECISION NULL,
    position_z          DOUBLE PRECISION NULL,

    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_celestial_bodies_parent_id
    ON celestial_bodies (parent_id);
CREATE INDEX IF NOT EXISTS idx_celestial_bodies_kind
    ON celestial_bodies (kind);
CREATE INDEX IF NOT EXISTS idx_celestial_bodies_solar_system_id
    ON celestial_bodies (solar_system_id);

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
    epoch                 DOUBLE PRECISION NOT NULL DEFAULT 0,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
