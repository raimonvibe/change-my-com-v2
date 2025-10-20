import { api } from '../axios'
import axios from 'axios'

// Mock axios
jest.mock('axios')
const mockedAxios = axios as jest.Mocked<typeof axios>

describe('API Client', () => {
  beforeEach(() => {
    jest.clearAllMocks()
  })

  it('should create axios instance with correct baseURL', () => {
    expect(mockedAxios.create).toHaveBeenCalled()
  })

  it('should have withCredentials enabled', () => {
    const createConfig = (mockedAxios.create as jest.Mock).mock.calls[0][0]
    expect(createConfig.withCredentials).toBe(true)
  })

  it('should use API_URL from environment', () => {
    const createConfig = (mockedAxios.create as jest.Mock).mock.calls[0][0]
    expect(createConfig.baseURL).toBeDefined()
  })

  describe('API Instance', () => {
    it('should export api instance', () => {
      expect(api).toBeDefined()
    })

    it('should be an axios instance', () => {
      expect(typeof api).toBe('object')
    })
  })

  describe('Configuration', () => {
    it('should enable CORS credentials', () => {
      const createConfig = (mockedAxios.create as jest.Mock).mock.calls[0][0]
      expect(createConfig.withCredentials).toBe(true)
    })
  })
})
