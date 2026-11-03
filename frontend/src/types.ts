export interface Flight {
  flightKey: string;
  flightNumber: string;
  date: string;
  origin: string;
  destination: string;
  departure: string;
  arrival: string;
  boardingCloses: string;
}
export interface FlightStatus {
  flight: Flight;
  delayMinutes: number;
  sequence: number;
  seats: number;
}
export interface Passenger {
  id: string;
  name: string;
  preferences: string;
  allowOvernight: boolean;
}
export interface Trip {
  tripId: string;
  passengerId: string;
  revision: number;
  status: string;
  legs: Flight[];
}
export interface Risk {
  tripId: string;
  passengerId: string;
  revision: number;
  status: string;
  slackMinutes: number;
}
export interface Itinerary {
  optionId: string;
  legs: Flight[];
  overnight: boolean;
}
export interface Proposal {
  id: string;
  tripId: string;
  passengerId: string;
  revision: number;
  status: string;
  deadline: string;
  workflowId: string;
  explanation: string;
  source: string;
  recommendedOptionId: string;
  options: Itinerary[];
}
