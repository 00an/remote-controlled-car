export type StatusMessage = {
  message: string;
  type: 'success' | 'error';
};

export type User = {
  firstName?: string;
  lastName?: string;
  fullname?: string;
  email?: string;
  username?: string;
  password?: string;
  token?: string;
};

export type Ride = {
  id: number;
  topspeed: number;
  averagespeed: number;
  timespent: number;
};
