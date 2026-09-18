import { User } from '@/types';
import { API_BASE } from '@/lib/api';

// credentials: 'include' is needed so the browser sends the HttpOnly authToken cookie along
const authenticate = async (user: User): Promise<User> => {
  const response = await fetch(`${API_BASE}/users/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(user),
    credentials: 'include',
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error?.error || error?.message || `Authentication failed.`);
  }

  return response.json();
};

const logout = async (): Promise<void> => {
  const response = await fetch(`${API_BASE}/users/logout`, {
    method: 'POST',
    credentials: 'include',
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error?.message || `Logout failed. Check server logs.`);
  }
};

const signup = async (user: {
  username: string;
  password: string;
  firstName: string;
  lastName: string;
  email: string;
  privacyConsent: boolean;
}) => {
  const response = await fetch(`${API_BASE}/users/signup`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(user),
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error?.message || 'Signup failed.');
  }

  return response.json();
};

const deleteAccount = async (): Promise<void> => {
  const response = await fetch(`${API_BASE}/users/me`, {
    method: 'DELETE',
    credentials: 'include',
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error?.message || 'Account deletion failed.');
  }
};

const UserService = {
  authenticate,
  logout,
  signup,
  deleteAccount,
};

export default UserService;
