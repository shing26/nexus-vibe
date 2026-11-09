import { useQuery } from '@tanstack/react-query';
import { apiClient } from './client';
import type { ApiResponse } from './client';

/**
 * One degraded capability, exactly as the server shapes it.
 *
 * <p>`key` is a stable identifier from a closed vocabulary and is the unit of dismissal;
 * `impact` is a sentence written for a visitor. The component names, statuses and probe
 * reasons behind them stay on the server (ADR-0007 keeps that surface closed).</p>
 */
export interface DegradedCapability {
  key: string;
  impact: string;
}

/**
 * The degradation banner's data source.
 *
 * <p>A request on mount plus a slow refetch, as the contract decided: the state changes on
 * the order of minutes, so a stream would buy nothing and keep a connection open through the
 * tunnel. A failed request rejects into react-query's error state and the banner renders
 * nothing - a broken banner is worse than no banner.</p>
 */
export function useDegradedCapabilities() {
  return useQuery<DegradedCapability[]>({
    queryKey: ['status', 'capabilities'],
    queryFn: async () => (await apiClient.get<ApiResponse<DegradedCapability[]>>('/status/capabilities')).data.data ?? [],
    staleTime: 1000 * 30,
    refetchInterval: 1000 * 60,
    retry: 1,
  });
}
