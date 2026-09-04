 import { useQuery } from '@tanstack/react-query';
 import { apiClient } from './client';
 
 export interface Channel {
   id: number;
   name: string;
   description: string;
   slug: string;
   sortOrder: number;
 }
 
 export function useChannels() {
   return useQuery<Channel[]>({
     queryKey: ['channels'],
     queryFn: () => apiClient.get('/channels').then(res => res.data.data),
     staleTime: 5 * 60 * 1000,
   });
 }
