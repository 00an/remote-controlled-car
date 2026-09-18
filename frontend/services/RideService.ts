import { Ride } from '@/types';
import { API_BASE } from '@/lib/api';

const getRideHistory = async (): Promise<Ride[]> => {
  const response = await fetch(`${API_BASE}/rides`, {
    credentials: 'include',
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error?.message || 'Failed to load ride history.');
  }

  return response.json();
};

const RideService = {
  getRideHistory,
};

export default RideService;
