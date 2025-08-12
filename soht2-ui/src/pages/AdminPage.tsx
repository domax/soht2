import React from 'react';
import { Layout } from '../components/Layout';
import { Navigate } from 'react-router-dom';
import { httpClient, type Soht2User } from '../api/soht2Api.ts';

export function AdminPage({ user }: Readonly<{ user?: Soht2User | null }>) {
  if ((user?.role || '').toUpperCase() !== 'ADMIN') {
    httpClient.clearAuth();
    return <Navigate to="/login" replace />;
  }
  return <Layout>{/* Admin page content */}</Layout>;
}
