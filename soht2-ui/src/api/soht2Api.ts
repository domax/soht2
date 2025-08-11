/* SOHT2 © Licensed under MIT 2025. */

// Minimal client UI API for soht2-ui matching soht2-server UserController and ConnectionController
// - Uses fetch with JSON handling and optional Basic Auth header
// - Provides typed functions for each controller endpoint

export type UUID = string;

// ===== Common DTOs =====
export interface Soht2User {
  username: string;
  role?: string | null;
  createdAt?: string | null; // ISO date-time
  updatedAt?: string | null; // ISO date-time
  allowedTargets?: string[] | null;
}

export interface Soht2Connection {
  id: UUID;
  user?: Soht2User | null;
  clientHost?: string | null;
  targetHost?: string | null;
  targetPort?: number | null;
  openedAt?: string | null; // ISO date-time
  closedAt?: string | null; // ISO date-time
}

// Paging related
export type SortingDir = 'ASC' | 'DESC' | 'asc' | 'desc';
export type HistorySorting =
  | 'userName'
  | 'connectionId'
  | 'clientHost'
  | 'targetHost'
  | 'targetPort'
  | 'openedAt'
  | 'closedAt';

export interface SortingOrder<F extends string> {
  field: F;
  direction: SortingDir;
}

export interface Paging<F extends string> {
  pageNumber: number;
  pageSize: number;
  sorting?: SortingOrder<F>[] | null;
}

export interface Page<T, F extends string> {
  paging?: Paging<F> | null;
  totalItems?: number | null;
  data?: T[] | null;
  totalPages?: number | null; // computed by server
}

export type HistoryOrder = SortingOrder<HistorySorting>;
export type HistoryPaging = Paging<HistorySorting> & { sorting?: HistoryOrder[] | null };
export type HistoryPage = Page<Soht2Connection, HistorySorting> & { paging?: HistoryPaging | null };

// ===== HTTP helper =====
export interface ApiClientOptions {
  baseUrl?: string; // default: '' (same origin)
  credentials?: { username: string; password: string } | null;
}

type QueryValue =
  | string
  | number
  | boolean
  | null
  | undefined
  | Array<string | number | boolean | null | undefined>;
type Query = Record<string, QueryValue>;

class HttpClient {
  private baseUrl: string;
  private authHeader: string | null = null;

  constructor(opts?: ApiClientOptions) {
    this.baseUrl = opts?.baseUrl ?? '';
    if (opts?.credentials) {
      this.setBasicAuth(opts.credentials.username, opts.credentials.password);
    }
  }

  setBasicAuth(username: string, password: string) {
    this.authHeader = 'Basic ' + btoa(`${username}:${password}`);
  }

  clearAuth() {
    this.authHeader = null;
  }

  private headers(extra?: HeadersInit, contentType?: string): HeadersInit {
    const headers: Record<string, string> = {};
    if (contentType) headers['Content-Type'] = contentType;
    if (this.authHeader) headers['Authorization'] = this.authHeader;
    return { ...headers, ...(extra ?? {}) };
  }

  private makeUrl(path: string, query?: Query): string {
    const url = new URL((this.baseUrl || '') + path, window.location.origin);
    if (query) {
      for (const [k, v] of Object.entries(query)) {
        if (Array.isArray(v)) {
          for (const item of v) {
            if (item !== undefined && item !== null) url.searchParams.append(k, String(item));
          }
        } else if (v !== undefined && v !== null) {
          url.searchParams.set(k, String(v));
        }
      }
    }
    return url.toString().replace(window.location.origin, '');
  }

  async getJson<T>(path: string, query?: Query): Promise<T> {
    const res = await fetch(this.makeUrl(path, query), { method: 'GET', headers: this.headers() });
    if (!res.ok) throw await this.toError(res);
    return (await res.json()) as T;
  }

  async postJson<T>(path: string, body?: unknown, query?: Query): Promise<T> {
    const res = await fetch(this.makeUrl(path, query), {
      method: 'POST',
      headers: this.headers(undefined, 'application/json'),
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
    if (!res.ok) throw await this.toError(res);
    return (await res.json()) as T;
  }

  async putJson<T>(path: string, body?: unknown, query?: Query): Promise<T> {
    const res = await fetch(this.makeUrl(path, query), {
      method: 'PUT',
      headers: this.headers(undefined, 'application/json'),
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
    if (!res.ok) throw await this.toError(res);
    return (await res.json()) as T;
  }

  async delete(path: string, query?: Query): Promise<void> {
    const res = await fetch(this.makeUrl(path, query), {
      method: 'DELETE',
      headers: this.headers(),
    });
    if (!res.ok) throw await this.toError(res);
  }

  async postOctet(
    path: string,
    data?: Uint8Array | ArrayBuffer | null,
    extraHeaders?: HeadersInit
  ): Promise<Uint8Array> {
    const res = await fetch(this.makeUrl(path), {
      method: 'POST',
      headers: this.headers({ ...(extraHeaders || {}) }, 'application/octet-stream'),
      body: data ? (data instanceof Uint8Array ? data : new Uint8Array(data)) : null,
    });
    if (!res.ok) throw await this.toError(res);
    const arrayBuf = await res.arrayBuffer();
    return new Uint8Array(arrayBuf);
  }

  private async toError(res: Response): Promise<Error> {
    let message = `${res.status} ${res.statusText}`;
    try {
      const text = await res.text();
      if (text) message += `: ${text}`;
    } catch {
      // ignore
    }
    return new Error(message);
  }
}

// Export a singleton client with defaults; consumers may create their own.
export const httpClient = new HttpClient();
export const createHttpClient = (opts?: ApiClientOptions) => new HttpClient(opts);

// ===== UserController API =====
export const UserApi = {
  // POST /api/user?username=&password=&role?=&target=*
  createUser: async (
    params: {
      username: string;
      password: string;
      role?: string | null;
      allowedTargets?: string[] | null; // maps to repeated target
    },
    client: HttpClient = httpClient
  ): Promise<Soht2User> => {
    const query: Query = { username: params.username, password: params.password };
    if (params.role != null) query['role'] = params.role;
    if (params.allowedTargets?.length) query['target'] = params.allowedTargets;
    return client.postJson<Soht2User>('/api/user', undefined, query);
  },

  // PUT /api/user/{name}?password?=&role?=&target?=
  updateUser: async (
    name: string,
    params: { password?: string | null; role?: string | null; allowedTargets?: string[] | null },
    client: HttpClient = httpClient
  ): Promise<Soht2User> => {
    const query: Query = {};
    if (params.password != null) query['password'] = params.password;
    if (params.role != null) query['role'] = params.role;
    if (params.allowedTargets) query['target'] = params.allowedTargets;
    return client.putJson<Soht2User>(`/api/user/${encodeURIComponent(name)}`, undefined, query);
  },

  // DELETE /api/user/{name}?force=&history=
  deleteUser: async (
    name: string,
    opts?: { force?: boolean; history?: boolean },
    client: HttpClient = httpClient
  ): Promise<void> => {
    const query: Query = { force: opts?.force ?? false, history: opts?.history ?? false };
    return client.delete(`/api/user/${encodeURIComponent(name)}`, query);
  },

  // GET /api/user
  listUsers: async (client: HttpClient = httpClient): Promise<Soht2User[]> => {
    return client.getJson<Soht2User[]>('/api/user');
  },

  // GET /api/user/self
  getSelf: async (client: HttpClient = httpClient): Promise<Soht2User> => {
    return client.getJson<Soht2User>('/api/user/self');
  },

  // PUT /api/user/self?old=&new=
  changePassword: async (
    params: { old: string; new: string },
    client: HttpClient = httpClient
  ): Promise<Soht2User> => {
    return client.putJson<Soht2User>('/api/user/self', undefined, {
      old: params.old,
      new: params.new,
    });
  },
};

// ===== ConnectionController API =====
export const ConnectionApi = {
  // POST /api/connection?host=&port=
  open: async (
    params: { host: string; port: number },
    client: HttpClient = httpClient
  ): Promise<Soht2Connection> => {
    return client.postJson<Soht2Connection>('/api/connection', undefined, {
      host: params.host,
      port: params.port,
    });
  },

  // GET /api/connection
  list: async (client: HttpClient = httpClient): Promise<Soht2Connection[]> => {
    return client.getJson<Soht2Connection[]>('/api/connection');
  },

  // GET /api/connection/history with many filters
  history: async (
    filters: {
      un?: string[];
      id?: UUID[];
      ch?: string;
      th?: string;
      tp?: number[];
      oa?: string; // ISO local datetime
      ob?: string;
      ca?: string;
      cb?: string;
      sort?: string[]; // e.g., ["openedAt:desc"]
      pg?: number; // default 0
      sz?: number; // default 10
    } = {},
    client: HttpClient = httpClient
  ): Promise<HistoryPage> => {
    return client.getJson<HistoryPage>('/api/connection/history', filters as Query);
  },

  // POST /api/connection/{id} octet-stream, optional Content-Encoding header
  exchange: async (
    id: UUID,
    data?: Uint8Array | ArrayBuffer | null,
    options?: { contentEncoding?: string },
    client: HttpClient = httpClient
  ): Promise<Uint8Array> => {
    const headers: HeadersInit = {};
    if (options?.contentEncoding) headers['Content-Encoding'] = options.contentEncoding;
    return client.postOctet(`/api/connection/${encodeURIComponent(id)}`, data ?? null, headers);
  },

  // DELETE /api/connection/{id}
  close: async (id: UUID, client: HttpClient = httpClient): Promise<void> => {
    return client.delete(`/api/connection/${encodeURIComponent(id)}`);
  },
};

export type { HttpClient };
