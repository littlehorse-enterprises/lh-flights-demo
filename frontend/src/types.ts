export interface Outcomes {
  trucksSimulated: number
  serviceEventsProcessed: number
  exceptionsDetected: number
  interactionsWithoutPhoneCall: number
  correctivePickupsSold: number
  additionalTrashPurchases: number
  incrementalRevenue: number
  failedServicesRecovered: number
  ledgerEntries: number
  simulationRunning: boolean
  elapsedSeconds: number
}

export interface ActivityItem {
  id: string
  time: string
  type: string
  title: string
  detail: string
  customerId: string
  truckId: string
  status: string
}

export interface TaskField {
  name: string
  type: string
  displayName: string
  description: string
  required: boolean
}

export interface UserTask {
  wfRunId: string
  userTaskGuid: string
  formName: string
  status: string
  userId: string | null
  userGroup: string | null
  notes: string | null
  scheduledTime: string
  fields: TaskField[]
}
