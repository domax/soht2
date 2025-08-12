import React from 'react';
import { Layout } from '../components/Layout';
import { Navigate } from 'react-router-dom';
import { httpClient, type Soht2User } from '../api/soht2Api.ts';

export function UserPage({ user }: Readonly<{ user?: Soht2User | null }>) {
  if ((user?.role || '') === '') {
    httpClient.clearAuth();
    return <Navigate to="/login" replace />;
  }
  return <Layout>{/* User page content */}</Layout>;
}
