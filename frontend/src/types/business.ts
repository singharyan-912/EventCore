export interface BusinessRegistrationRequest {
  businessName: string
  ownerName: string
  email: string
  password: string
  phone?: string | null
}

export interface BusinessRegistrationResponse {
  tenantId: string
  businessName: string
  ownerUsername: string
  ownerEmail: string
  status: string
  createdAt: string
  message: string
}
