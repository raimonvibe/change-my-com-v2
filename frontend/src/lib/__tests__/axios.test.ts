import { api } from '../axios'

describe('API Client', () => {
  describe('API Instance', () => {
    it('should export api instance', () => {
      expect(api).toBeDefined()
    })

    it('should be an axios instance', () => {
      expect(api).toHaveProperty('get')
      expect(api).toHaveProperty('post')
      expect(api).toHaveProperty('put')
      expect(api).toHaveProperty('delete')
      expect(api).toHaveProperty('defaults')
    })
  })

  describe('Configuration', () => {
    it('should have baseURL configured', () => {
      expect(api.defaults.baseURL).toBeDefined()
    })

    it('should enable CORS credentials', () => {
      expect(api.defaults.withCredentials).toBe(true)
    })

    it('should have withCredentials enabled', () => {
      expect(api.defaults.withCredentials).toBe(true)
    })
  })
})
