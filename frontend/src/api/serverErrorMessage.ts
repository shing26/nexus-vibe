import { isAxiosError } from 'axios';

/**
 * The sentence the server wrote for this failure, or `fallback`.
 *
 * <p>Three admin panels used to render `(error as Error)?.message`, which is
 * correct only while the transport agrees with the body. Until round six a
 * permission refusal arrived as `200` with `code: 403` in the envelope, so the
 * query resolved, `data` came back null, and no error branch was ever entered.
 * Now that the status is the truth, that same failure rejects, and the string
 * axios keeps for itself is `Request failed with status code 403` - true, but
 * not something to show a user. The envelope's `message` is the one written for
 * them, so it wins here and nowhere else.
 */
export function serverErrorMessage(error: unknown, fallback: string): string {
  if (isAxiosError(error)) {
    const envelope = error.response?.data as { message?: unknown } | undefined;
    const message = typeof envelope?.message === 'string' ? envelope.message.trim() : '';
    if (message) return message;
    // A request that never reached the server has no envelope; axios's own line
    // ("Network Error", "timeout of 0ms exceeded") is the honest description.
    if (!error.response && error.message) return error.message;
    return fallback;
  }
  if (error instanceof Error && error.message) return error.message;
  return fallback;
}
