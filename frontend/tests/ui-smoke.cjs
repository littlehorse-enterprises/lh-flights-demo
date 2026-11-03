const { chromium } = require("playwright");
const fs = require("fs");
const http = require("http");
const path = require("path");
(async () => {
  const browser = await chromium.launch({
    headless: true,
    ...(process.env.CHROMIUM_EXECUTABLE
      ? { executablePath: process.env.CHROMIUM_EXECUTABLE }
      : {}),
    args: ["--no-sandbox"],
  });
  const page = await browser.newPage({
    viewport: { width: 1440, height: 1100 },
  });
  const root = path.resolve(__dirname, "../..");
  const server = http.createServer((req, res) => {
    let file = path.join(
      root,
      "frontend/dist",
      new URL(req.url, "http://localhost").pathname,
    );
    if (!fs.existsSync(file) || !fs.statSync(file).isFile())
      file = path.join(root, "frontend/dist/index.html");
    res.setHeader(
      "Content-Type",
      file.endsWith(".js")
        ? "text/javascript"
        : file.endsWith(".css")
          ? "text/css"
          : "text/html",
    );
    res.end(fs.readFileSync(file));
  });
  await new Promise((resolve) => server.listen(5188, "127.0.0.1", resolve));
  const flights = JSON.parse(
    fs.readFileSync(root + "/fixtures/flights.json", "utf8"),
  )
    .slice(0, 12)
    .map((f) => ({
      ...f,
      delayMinutes: f.flight.flightNumber === "LH101" ? 25 : 0,
      sequence: 2,
    }));
  const passengers = JSON.parse(
    fs.readFileSync(root + "/fixtures/passengers.json", "utf8"),
  );
  const trip = {
    tripId: "demo123",
    passengerId: "p001",
    revision: 1,
    status: "CONFIRMED",
    legs: [flights[0].flight, flights[1].flight],
  };
  let proposal = {
    id: "offer-demo123-v1",
    tripId: "demo123",
    passengerId: "p001",
    revision: 1,
    status: "PENDING",
    deadline: new Date(Date.now() + 180000).toISOString(),
    workflowId: "recover-demo123-v1",
    explanation:
      "The direct flight gets you to Boston tonight with enough time to transfer in Denver.",
    source: "Rules",
    recommendedOptionId: "direct",
    options: [
      {
        optionId: "direct",
        legs: [flights[0].flight, flights[3].flight],
        overnight: false,
      },
      {
        optionId: "ord",
        legs: [flights[0].flight, flights[4].flight, flights[5].flight],
        overnight: false,
      },
    ],
  };
  let posts = [];
  let errors = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.route("**/api/**", async (route) => {
    const path = new URL(route.request().url()).pathname.replace("/api", "");
    if (route.request().method() === "POST") {
      posts.push({ path, data: route.request().postDataJSON() });
      if (path.endsWith("/response"))
        proposal = { ...proposal, status: "REBOOKED" };
      return route.fulfill({ json: { status: "DELIVERED" } });
    }
    const values = {
      "/flights": flights,
      "/passengers": passengers,
      "/trips": [trip],
      "/trips/demo123": trip,
      "/connections/at-risk": [
        {
          tripId: "demo123",
          passengerId: "p001",
          revision: 1,
          status: "AT_RISK",
          slackMinutes: -10,
        },
      ],
      "/rebooking-proposals": [proposal],
      "/rebooking-proposals/offer-demo123-v1": proposal,
    };
    await route.fulfill({ json: values[path] || [] });
  });
  await page.goto("http://127.0.0.1:5188/backoffice");
  await page.getByText("Ava Chen", { exact: true }).first().waitFor();

  await page.getByRole("button", { name: "Publish flight status" }).click();
  if (!posts.some((p) => p.path.includes("/delay") && p.data.minutes === 120))
    throw new Error("Delay request missing");
  await page.goto("http://127.0.0.1:5188/passenger/offer-demo123-v1");
  await page.getByRole("button", { name: "Confirm this itinerary" }).waitFor();

  await page.getByRole("button", { name: "Confirm this itinerary" }).click();
  await page
    .getByRole("heading", { name: "New itinerary confirmed" })
    .waitFor();
  if (
    !posts.some(
      (p) =>
        p.path.endsWith("/response") &&
        p.data.accepted &&
        p.data.optionId === "direct",
    )
  )
    throw new Error("Acceptance request missing");
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("http://127.0.0.1:5188/backoffice");
  await page.getByText("Keep every journey moving.").waitFor();
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth > innerWidth,
  );
  if (overflow) throw new Error("Mobile overflow");
  if (errors.length) throw new Error(errors.join("\n"));
  console.log(
    "PASS desktop backoffice, passenger acceptance, delay publication, mobile layout; mocked API",
  );
  await browser.close();
  server.close();
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
