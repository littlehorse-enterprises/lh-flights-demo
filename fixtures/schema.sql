CREATE TABLE passengers (id text PRIMARY KEY, name text NOT NULL, preferences text NOT NULL, allow_overnight boolean NOT NULL);
CREATE TABLE flights (flight_key text PRIMARY KEY, flight_number text NOT NULL, departure_date text NOT NULL, origin text NOT NULL, destination text NOT NULL, departure timestamptz NOT NULL, arrival timestamptz NOT NULL, boarding_closes timestamptz NOT NULL, seats integer NOT NULL CHECK(seats >= 0), delay_minutes integer NOT NULL DEFAULT 0, sequence bigint NOT NULL DEFAULT 1);
CREATE TABLE trips (trip_id text PRIMARY KEY, passenger_id text NOT NULL REFERENCES passengers(id), revision integer NOT NULL, status text NOT NULL, data jsonb NOT NULL);
CREATE TABLE proposals (id text PRIMARY KEY, trip_id text NOT NULL REFERENCES trips(trip_id), revision integer NOT NULL, status text NOT NULL, deadline timestamptz NOT NULL, data jsonb NOT NULL, decision jsonb, UNIQUE(trip_id, revision));
CREATE TABLE reservation_results (trip_id text NOT NULL, expected_revision integer NOT NULL, data jsonb NOT NULL, PRIMARY KEY(trip_id, expected_revision));
CREATE TABLE booking_requests (id text PRIMARY KEY, data jsonb NOT NULL);
