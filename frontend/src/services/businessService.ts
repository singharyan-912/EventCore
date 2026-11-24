import { api } from './api'
import type { BusinessRegistrationRequest, BusinessRegistrationResponse } from '../types/business'

export const businessService = {
  registerBusiness: async (request: BusinessRegistrationRequest): Promise<BusinessRegistrationResponse> => {
    const { data } = await api.post<BusinessRegistrationResponse>('/businesses/register', request)
    return data
  },
}
