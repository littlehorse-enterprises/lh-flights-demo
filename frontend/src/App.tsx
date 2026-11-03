import { useEffect, useState } from "react";
import type {
  FlightStatus,
  Passenger,
  Proposal,
  Risk,
  Trip,
  Itinerary,
} from "./types";

async function api<T>(path: string, body?: unknown): Promise<T> {
  const response = await fetch(
    `/api${path}`,
    body === undefined
      ? {}
      : {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body),
        },
  );
  if (!response.ok)
    throw new Error(
      (await response.text()) || `Request failed (${response.status})`,
    );
  return response.status === 204 ? (undefined as T) : response.json();
}
const time = (s: string) =>
  new Date(s).toLocaleTimeString("en-GB", {
    hour: "2-digit",
    minute: "2-digit",
    timeZone: "UTC",
  });
const label = (s: string) => s.replaceAll("_", " ").toLowerCase();
const workflow = (id: string) =>
  `${window.location.protocol}//${window.location.hostname}:3000/default/wfRun/${encodeURIComponent(id)}`;
function Mark() {
  return (
    <span className="brand-mark">
      <svg viewBox="0 0 24 24">
        <path
          d="m3 14 8-3 3-8 2 1-1 7 6 4-1 2-7-2-3 5-2-1 1-5-5 2z"
          fill="currentColor"
        />
      </svg>
    </span>
  );
}
function RouteMap({ trips }: { trips: Trip[] }) {
  const points: Record<string, [number, number]> = {
    RNO: [100, 100],
    SLC: [185, 105],
    DEN: [275, 135],
    SEA: [100, 35],
    LAX: [80, 190],
    SFO: [40, 135],
    ATL: [510, 240],
    DTW: [465, 100],
    ORD: [400, 125],
    BOS: [635, 85],
    JFK: [610, 125],
    DFW: [350, 260],
    MIA: [570, 310],
    MSP: [350, 70],
    PHX: [170, 235],
    IAD: [565, 170],
    PDX: [65, 65],
    SAN: [95, 220],
    LAS: [145, 170],
    MCO: [560, 275],
  };
  const routes = new Map<string, [string, string]>();
  for (const t of trips)
    for (const l of t.legs)
      routes.set(`${l.origin}-${l.destination}`, [l.origin, l.destination]);
  if (!routes.size) {
    routes.set("RNO-DEN", ["RNO", "DEN"]);
    routes.set("DEN-BOS", ["DEN", "BOS"]);
    routes.set("DEN-SEA", ["DEN", "SEA"]);
  }
  return (
    <svg
      className="route-map"
      viewBox="0 0 700 350"
      role="img"
      aria-label="Schematic route map of booked flights"
    >
      <defs>
        <pattern id="dots" width="20" height="20" patternUnits="userSpaceOnUse">
          <circle cx="2" cy="2" r="1" fill="#26354c" />
        </pattern>
      </defs>
      <rect width="700" height="350" fill="url(#dots)" />
      <path
        d="M35 50 100 20 230 45 330 40 380 65 460 50 480 80 565 70 620 55 660 90 620 170 565 200 590 320 550 315 505 255 440 265 380 295 335 290 315 260 235 260 170 240 95 235 60 175Z"
        fill="#152237"
        stroke="#2c3b53"
        strokeWidth="1.5"
      />
      {[...routes.values()].map(([a, b]) => {
        const x = points[a],
          y = points[b];
        if (!x || !y) return null;
        return (
          <path
            key={`${a}-${b}`}
            d={`M${x[0]} ${x[1]} Q${(x[0] + y[0]) / 2} ${Math.min(x[1], y[1]) - 35} ${y[0]} ${y[1]}`}
            fill="none"
            stroke={a === "RNO" ? "#ffad68" : "#4eb6b8"}
            strokeWidth="1.5"
            opacity=".6"
          />
        );
      })}
      {Object.entries(points)
        .filter(([city]) => [...routes.values()].some((r) => r.includes(city)))
        .map(([city, [x, y]]) => (
          <g key={city}>
            <circle
              cx={x}
              cy={y}
              r="5"
              fill={city === "DEN" ? "#ffad68" : "#6ce0da"}
            />
            <circle
              cx={x}
              cy={y}
              r="11"
              fill="none"
              stroke="#6ce0da"
              opacity=".2"
            />
            <text
              x={x + 12}
              y={y + 4}
              fill="#e8eff9"
              fontSize="12"
              fontWeight="600"
            >
              {city}
            </text>
          </g>
        ))}
      <text x="20" y="330" fill="#74859d" fontSize="11">
        SCHEMATIC · ACTIVE BOOKED ROUTES
      </text>
    </svg>
  );
}
function Footer() {
  return (
    <footer>
      <span>Synthetic airline data · Schedule times in UTC</span>
      <span>Let us love one another. · 1 John 4:7</span>
    </footer>
  );
}
export default function App() {
  const passengerRoute = window.location.pathname.match(/^\/passenger\/(.+)$/);
  return passengerRoute ? (
    <PassengerView id={decodeURIComponent(passengerRoute[1])} />
  ) : (
    <Backoffice />
  );
}
function Backoffice() {
  const [flights, setFlights] = useState<FlightStatus[]>([]),
    [passengers, setPassengers] = useState<Passenger[]>([]),
    [trips, setTrips] = useState<Trip[]>([]),
    [proposals, setProposals] = useState<Proposal[]>([]),
    [risks, setRisks] = useState<Risk[]>([]);
  const [error, setError] = useState(""),
    [selected, setSelected] = useState(""),
    [minutes, setMinutes] = useState(120),
    [busy, setBusy] = useState(false),
    [notice, setNotice] = useState(""),
    [search, setSearch] = useState("");
  useEffect(() => {
    let active = true;
    async function refresh() {
      try {
        const [f, p, t, o, r] = await Promise.all([
          api<FlightStatus[]>("/flights"),
          api<Passenger[]>("/passengers"),
          api<Trip[]>("/trips"),
          api<Proposal[]>("/rebooking-proposals"),
          api<Risk[]>("/connections/at-risk"),
        ]);
        if (active) {
          setFlights(f);
          setPassengers(p);
          setTrips(t);
          setProposals(o);
          setRisks(r);
          setError("");
          setSelected(
            (current) =>
              current ||
              f.find((s) => s.flight.flightNumber === "LH101")?.flight
                .flightKey ||
              f[0]?.flight.flightKey ||
              "",
          );
        }
      } catch (e) {
        if (active) setError((e as Error).message);
      }
    }
    void refresh();
    const timer = setInterval(() => void refresh(), 2000);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, []);
  const people = new Map(passengers.map((p) => [p.id, p])),
    pending = proposals.filter((p) => p.status === "PENDING"),
    finished = proposals.filter((p) => p.status !== "PENDING");
  const activeTrips = trips.filter((t) => t.status === "CONFIRMED"),
    delayed = flights.filter((f) => f.delayMinutes > 0);
  const bookedOnFlight = new Map<string, number>();
  for (const trip of activeTrips)
    for (const leg of trip.legs)
      bookedOnFlight.set(
        leg.flightKey,
        (bookedOnFlight.get(leg.flightKey) || 0) + 1,
      );
  const selectableFlights = [...flights].sort(
    (a, b) =>
      (bookedOnFlight.get(b.flight.flightKey) || 0) -
      (bookedOnFlight.get(a.flight.flightKey) || 0),
  );
  const filter = flights.filter((s) =>
    `${s.flight.flightNumber} ${s.flight.origin} ${s.flight.destination}`
      .toLowerCase()
      .includes(search.toLowerCase()),
  );
  async function delay() {
    setBusy(true);
    try {
      await api(`/flights/${encodeURIComponent(selected)}/delay`, { minutes });
      setNotice(
        `${selected.split("-")[0]} updated to ${minutes} minutes delay.`,
      );
      setError("");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="shell">
      <aside>
        <a className="wordmark" href="/backoffice">
          <Mark />
          <span>
            CONNECTION
            <br />
            <b>RESCUE</b>
          </span>
        </a>
        <div className="nav-label">AIRLINE OPERATIONS</div>
        <a className="nav-item active" href="/backoffice">
          <span>◈</span> Control room
        </a>
        <div className="aside-bottom">
          <div className="live-dot" /> Event driven operations
          <p>Kafka + LittleHorse</p>
        </div>
      </aside>
      <main>
        <header>
          <div>
            <p className="eyebrow">
              NETWORK OPERATIONS / CONNECTION MANAGEMENT
            </p>
            <h1>Keep every journey moving.</h1>
            <p className="muted">
              From a flight delay to a confirmed new itinerary.
            </p>
          </div>
          <a
            className="button secondary"
            href={`${window.location.protocol}//${window.location.hostname}:3000`}
            target="_blank"
            rel="noreferrer"
          >
            Workflow dashboard ↗
          </a>
        </header>
        {error && (
          <div role="alert" className="alert">
            {error.slice(0, 240)}
          </div>
        )}
        <section className="metrics">
          <Metric
            title="BOOKED TRIPS"
            value={activeTrips.length}
            detail="Live from Kafka Streams"
          />
          <Metric
            title="DELAYED FLIGHTS"
            value={delayed.length}
            detail="Across the flight network"
          />
          <Metric
            title="AT-RISK CONNECTIONS"
            value={risks.length}
            detail="Only impacted passengers"
          />
          <Metric
            title="AWAITING A RESPONSE"
            value={pending.length}
            detail="Individual recovery workflows"
            accent
          />
        </section>
        <div className="network-grid">
          <section className="panel">
            <div className="panel-heading">
              <h2>Network view</h2>
              <span className="pill">
                <i /> Live booking view
              </span>
            </div>
            <RouteMap trips={activeTrips} />
          </section>
          <section className="panel delay-panel">
            <p className="eyebrow">FLIGHT OPERATIONS</p>
            <h2>Inject a delay</h2>
            <p className="muted">
              Update a flight’s operational status. Connections are evaluated
              automatically.
            </p>
            <label htmlFor="flight">Flight instance</label>
            <select
              id="flight"
              value={selected}
              onChange={(e) => setSelected(e.target.value)}
            >
              {selectableFlights.map((s) => (
                <option key={s.flight.flightKey} value={s.flight.flightKey}>
                  {s.flight.flightNumber} · {s.flight.origin} →{" "}
                  {s.flight.destination} · {s.flight.date}{" "}
                  {time(s.flight.departure)} ·{" "}
                  {bookedOnFlight.get(s.flight.flightKey) || 0} booked
                </option>
              ))}
            </select>
            <label htmlFor="delay">Total delay</label>
            <select
              id="delay"
              value={minutes}
              onChange={(e) => setMinutes(Number(e.target.value))}
            >
              {[0, 10, 25, 30, 60, 120, 180, 360].map((m) => (
                <option key={m} value={m}>
                  {m === 0 ? "Restore on time" : `${m} minutes`}
                </option>
              ))}
            </select>
            <button
              className="button primary"
              onClick={() => void delay()}
              disabled={busy || !selected}
            >
              {busy ? "Publishing…" : "Publish flight status"}
            </button>
            <p className="notice" aria-live="polite">
              {notice ||
                "Flights with booked trips appear first. Choose one to see affected passengers."}
            </p>
          </section>
        </div>
        <section className="panel offers">
          <div className="panel-heading">
            <div>
              <h2>
                Passengers who need a hand{" "}
                <span className="count">{pending.length}</span>
              </h2>
              <p className="muted">
                Open a passenger view to review and respond to an offer.
              </p>
            </div>
            <span className="eyebrow">REACCOMMODATION DESK</span>
          </div>
          {pending.length === 0 ? (
            <div className="empty">
              <span className="empty-orbit">↗</span>
              <h3>Ready when plans change.</h3>
              <p>
                Start booking traffic, then publish a delay to see recovery
                offers here.
              </p>
              <code>python3 simulator/simulate.py</code>
            </div>
          ) : (
            <div className="offer-grid">
              {pending.map((p) => (
                <a
                  className="offer-card"
                  href={`/passenger/${encodeURIComponent(p.id)}`}
                  target="_blank"
                  rel="noreferrer"
                  key={p.id}
                >
                  <div className="offer-top">
                    <span className="avatar">
                      {(people.get(p.passengerId)?.name || p.passengerId)
                        .split(" ")
                        .map((n) => n[0])
                        .join("")
                        .slice(0, 2)}
                    </span>
                    <span className="badge pending">Awaiting response</span>
                  </div>
                  <h3>{people.get(p.passengerId)?.name || p.passengerId}</h3>
                  <p className="muted">
                    PNR {p.tripId.toUpperCase()} · {p.source}
                  </p>
                  <p className="offer-explanation">{p.explanation}</p>
                  <div className="card-action">
                    Open passenger view <span>↗</span>
                  </div>
                </a>
              ))}
            </div>
          )}
        </section>
        <div className="detail-grid">
          <section className="panel">
            <div className="panel-heading">
              <h2>Departure board</h2>
              <input
                aria-label="Search flights"
                placeholder="Search flight or airport"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>
            <div className="board">
              <div className="board-row board-label">
                <span>FLIGHT / DATE</span>
                <span>ROUTE</span>
                <span>DEPARTS</span>
                <span>STATUS</span>
              </div>
              {filter.slice(0, 80).map((s) => (
                <div key={s.flight.flightKey} className="board-row">
                  <span>
                    <b>{s.flight.flightNumber}</b>
                    <small>{s.flight.date}</small>
                  </span>
                  <span>
                    {s.flight.origin} <span className="muted">→</span>{" "}
                    {s.flight.destination}
                  </span>
                  <span>{time(s.flight.departure)}</span>
                  <span
                    className={`badge ${s.delayMinutes ? "delayed" : "safe"}`}
                  >
                    {s.delayMinutes ? `+${s.delayMinutes} min` : "On time"}
                  </span>
                </div>
              ))}
            </div>
          </section>
          <section className="panel">
            <div className="panel-heading">
              <h2>Recent outcomes</h2>
            </div>
            <div className="outcomes">
              {finished.length === 0 ? (
                <p className="muted">
                  Accepted, declined and expired offers will appear here.
                </p>
              ) : (
                finished.slice(0, 12).map((p) => (
                  <div className="outcome" key={p.id}>
                    <div>
                      <b>{people.get(p.passengerId)?.name || p.passengerId}</b>
                      <p>
                        {p.tripId.toUpperCase()} ·{" "}
                        <a
                          href={workflow(p.workflowId)}
                          target="_blank"
                          rel="noreferrer"
                        >
                          View workflow ↗
                        </a>
                      </p>
                    </div>
                    <span
                      className={`badge ${p.status === "REBOOKED" ? "safe" : "delayed"}`}
                    >
                      {label(p.status)}
                    </span>
                  </div>
                ))
              )}
            </div>
            <div className="safe-trips">
              <p className="eyebrow">UNINTERRUPTED JOURNEYS</p>
              {activeTrips
                .filter((t) => !risks.some((r) => r.tripId === t.tripId))
                .slice(0, 6)
                .map((t) => (
                  <p key={t.tripId}>
                    {people.get(t.passengerId)?.name || t.passengerId}
                    <span>{t.legs.at(-1)?.destination} · on track</span>
                  </p>
                ))}
            </div>
          </section>
        </div>
        <Footer />
      </main>
    </div>
  );
}
function Metric({
  title,
  value,
  detail,
  accent = false,
}: {
  title: string;
  value: number;
  detail: string;
  accent?: boolean;
}) {
  return (
    <div className={`metric ${accent ? "accent" : ""}`}>
      <p>{title}</p>
      <strong>{value.toString().padStart(2, "0")}</strong>
      <span>{detail}</span>
    </div>
  );
}
function ItineraryCard({
  option,
  selected,
  recommended,
  onSelect,
  original,
}: {
  option: Itinerary;
  selected: boolean;
  recommended: boolean;
  onSelect: () => void;
  original?: Trip;
}) {
  const firstChanged = option.legs.findIndex(
    (leg, index) => leg.flightKey !== original?.legs[index]?.flightKey,
  );
  const replacement = option.legs.slice(Math.max(0, firstChanged));
  return (
    <button
      className={`itinerary ${selected ? "chosen" : ""}`}
      onClick={onSelect}
    >
      <div className="itinerary-top">
        <span className="radio" />
        {recommended ? (
          <span className="badge safe">Recommended</span>
        ) : (
          <span className="muted">Alternative itinerary</span>
        )}
        {option.overnight && <span className="badge delayed">Next day</span>}
      </div>
      {replacement.map((l) => (
        <div className="leg" key={l.flightKey}>
          <div>
            <strong>{time(l.departure)}</strong>
            <span>{l.origin}</span>
          </div>
          <div className="leg-line">
            <small>
              {l.flightNumber} · {l.date}
            </small>
            <i />
          </div>
          <div>
            <strong>{time(l.arrival)}</strong>
            <span>{l.destination}</span>
          </div>
        </div>
      ))}
    </button>
  );
}
function PassengerView({ id }: { id: string }) {
  const [proposal, setProposal] = useState<Proposal>(),
    [person, setPerson] = useState<Passenger>(),
    [trip, setTrip] = useState<Trip>(),
    [selected, setSelected] = useState(""),
    [error, setError] = useState(""),
    [sent, setSent] = useState(false),
    [busy, setBusy] = useState(false),
    [now, setNow] = useState(Date.now());
  useEffect(() => {
    let active = true;
    async function refresh() {
      try {
        const p = await api<Proposal>(
          `/rebooking-proposals/${encodeURIComponent(id)}`,
        );
        const [passengers, t] = await Promise.all([
          api<Passenger[]>("/passengers"),
          api<Trip>(`/trips/${p.tripId}`),
        ]);
        if (active) {
          setProposal(p);
          setTrip(t);
          setPerson(passengers.find((c) => c.id === p.passengerId));
          setSelected((s) => s || p.recommendedOptionId);
          setError("");
        }
      } catch (e) {
        if (active) setError((e as Error).message);
      }
    }
    void refresh();
    const timer = setInterval(() => {
      setNow(Date.now());
      void refresh();
    }, 2000);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [id]);
  async function respond(accepted: boolean) {
    setBusy(true);
    try {
      await api(`/rebooking-proposals/${encodeURIComponent(id)}/response`, {
        accepted,
        optionId: accepted ? selected : "",
      });
      setSent(true);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  const remaining = proposal
    ? Math.max(0, Math.ceil((Date.parse(proposal.deadline) - now) / 1000))
    : 0;
  const open = proposal?.status === "PENDING" && remaining > 0 && !sent;
  return (
    <div className="passenger-shell">
      <nav>
        <a className="passenger-brand" href="/backoffice">
          <Mark /> Connection Rescue
        </a>
        <span>YOUR JOURNEY, TAKEN CARE OF</span>
      </nav>
      <main className="passenger-main">
        {error && (
          <div className="alert" role="alert">
            {error.slice(0, 240)}
          </div>
        )}
        {!proposal ? (
          <p>Loading your itinerary…</p>
        ) : (
          <>
            <p className="eyebrow">
              HELLO, {person?.name.toUpperCase() || proposal.passengerId}
            </p>
            <h1>
              {proposal.status === "REBOOKED"
                ? "You're on your way."
                : "A change of plans. A way forward."}
            </h1>
            <p className="passenger-intro">
              {proposal.status === "REBOOKED"
                ? "Your new itinerary is confirmed. Your original confirmation number stays the same."
                : "Your connection needs a little extra care. We've looked at the alternatives so you can choose what works for you."}
            </p>
            <div className="journey-strip">
              <span>
                CONFIRMATION <b>{proposal.tripId.toUpperCase()}</b>
              </span>
              <span>
                {trip?.legs
                  .map((l) => l.origin)
                  .concat(trip.legs.at(-1)?.destination || "")
                  .join(" → ")}
              </span>
              <a
                href={workflow(proposal.workflowId)}
                target="_blank"
                rel="noreferrer"
              >
                View workflow ↗
              </a>
            </div>
            <section className="advisor-note">
              <div className="advisor-symbol">✦</div>
              <div>
                <p className="eyebrow">
                  YOUR REBOOKING ADVISOR · {proposal.source.toUpperCase()}
                </p>
                <h2>A recommendation with you in mind.</h2>
                <p>{proposal.explanation}</p>
                {person && (
                  <small>Your preferences: {person.preferences}</small>
                )}
              </div>
            </section>
            {proposal.status === "PENDING" ? (
              <>
                <div className="options-heading">
                  <h2>Choose your onward journey</h2>
                  <span className="badge pending">
                    {Math.floor(remaining / 60)}:
                    {String(remaining % 60).padStart(2, "0")} remaining
                  </span>
                </div>
                <div className="itinerary-grid">
                  {proposal.options.map((o) => (
                    <ItineraryCard
                      key={o.optionId}
                      option={o}
                      original={trip}
                      selected={selected === o.optionId}
                      recommended={proposal.recommendedOptionId === o.optionId}
                      onSelect={() => {
                        if (open) setSelected(o.optionId);
                      }}
                    />
                  ))}
                </div>
                {sent ? (
                  <div className="confirmation" role="status">
                    Response delivered. Your booking workflow is processing it…
                  </div>
                ) : remaining === 0 ? (
                  <div className="confirmation">
                    The response window has closed. Waiting for the workflow’s
                    final outcome…
                  </div>
                ) : (
                  <div className="decision-bar">
                    <button
                      className="button secondary"
                      disabled={busy || !open}
                      onClick={() => void respond(false)}
                    >
                      Keep my current booking
                    </button>
                    <button
                      className="button primary"
                      disabled={busy || !open || !selected}
                      onClick={() => void respond(true)}
                    >
                      {busy ? "Sending…" : "Confirm this itinerary"}
                    </button>
                  </div>
                )}
              </>
            ) : (
              <div
                className={`confirmation ${proposal.status === "REBOOKED" ? "success" : ""}`}
                role="status"
              >
                <h2>
                  {(
                    {
                      REBOOKED: "New itinerary confirmed",
                      DECLINED: "Your current booking is unchanged",
                      TIMED_OUT: "The offer has expired",
                      UNAVAILABLE: "These seats are no longer available",
                      MANUAL_ASSISTANCE: "Let an airport agent help",
                      RESOLVED: "Your connection is on track",
                    } as Record<string, string>
                  )[proposal.status] || label(proposal.status)}
                </h2>
                <p>
                  {["UNAVAILABLE", "MANUAL_ASSISTANCE", "TIMED_OUT"].includes(
                    proposal.status,
                  )
                    ? "Please contact an airport agent for help with your journey."
                    : "Thank you. You can review your journey using your original confirmation number."}
                </p>
                {proposal.status === "REBOOKED" &&
                  trip?.legs.map((l) => (
                    <p key={l.flightKey}>
                      {l.flightNumber} · {l.origin} → {l.destination} · {l.date}{" "}
                      · {time(l.departure)} UTC
                    </p>
                  ))}
              </div>
            )}
          </>
        )}
        <Footer />
      </main>
    </div>
  );
}
