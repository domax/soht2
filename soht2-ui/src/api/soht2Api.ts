/* SOHT2 © Licensed under MIT 2025. */
export type UUID = string; // NOSONAR typescript:S6564
export type ISODateTime = string; // NOSONAR typescript:S6564
export type UserRole = 'USER' | 'ADMIN';

export type WindowProps = typeof globalThis & { __CONTEXT_PATH__: string; __SWAGGER_URL__: string };

// ===== Common DTOs =====
export interface Soht2User {
  username: string;
  role?: UserRole | null;
  createdAt?: ISODateTime | null;
  updatedAt?: ISODateTime | null;
  allowedTargets?: string[] | null;
}

export interface Soht2Connection {
  id: UUID;
  user?: Soht2User | null;
  clientHost?: string | null;
  targetHost?: string | null;
  targetPort?: number | null;
  openedAt?: ISODateTime | null;
  closedAt?: ISODateTime | null;
  bytesRead?: number | null;
  bytesWritten?: number | null;
}

// Paging related
export type SortingDirUpper = 'ASC' | 'DESC';
export type SortingDirLower = 'asc' | 'desc';
export type SortingDir = SortingDirUpper | SortingDirLower;
export type HistorySortColumn =
  | 'connectionId'
  | 'userName'
  | 'clientHost'
  | 'targetHost'
  | 'targetPort'
  | 'openedAt'
  | 'closedAt'
  | 'bytesRead'
  | 'bytesWritten';

export type TableSorting<SortColumn extends string> = {
  column: SortColumn | null;
  direction: SortingDirLower | null;
};

export interface SortingOrder<SortColumn extends string> {
  field: SortColumn;
  direction: SortingDir;
}

export interface Paging<SortColumn extends string> {
  pageNumber: number;
  pageSize: number;
  sorting?: SortingOrder<SortColumn>[] | null;
}

export interface Page<DataType, SortColumn extends string> {
  paging?: Paging<SortColumn> | null;
  totalItems?: number | null;
  data?: DataType[] | null;
  totalPages?: number | null; // computed by server
}

export type HistoryOrder = SortingOrder<HistorySortColumn>;
export type HistoryPaging = Paging<HistorySortColumn> & { sorting?: HistoryOrder[] | null };
export type HistoryPage = Page<Soht2Connection, HistorySortColumn> & {
  paging?: HistoryPaging | null;
};

export type ValidationError = { defaultMessage: string; arguments: unknown[] };
export class ApiError extends Error {
  timestamp: ISODateTime;
  status: number;
  message: string;
  error?: string | null;
  errors?: ValidationError[] | null;
  path?: string | null;

  constructor(
    message: string,
    status: number = 400,
    timestamp: ISODateTime = new Date().toISOString(),
    error?: string,
    errors?: ValidationError[],
    path?: string
  ) {
    super();
    this.timestamp = timestamp;
    this.status = status;
    this.error = error;
    this.errors = errors;
    this.message = message;
    this.path = path;
  }
}

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
  private readonly baseUrl: string;
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

  private headers(contentType?: string, extra: HeadersInit = {}): HeadersInit {
    const headers: Record<string, string> = {};
    if (contentType) headers['Content-Type'] = contentType;
    if (this.authHeader) headers['Authorization'] = this.authHeader;
    return { ...headers, ...extra };
  }

  private makeUrl(path: string, query?: Query): string /* NOSONAR (typescript:S3776) */ {
    const url = new URL((this.baseUrl ?? '') + path, globalThis.location.origin);
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
    return url.toString().replace(globalThis.location.origin, '');
  }

  async getJson<T>(path: string, query?: Query): Promise<T> {
    const res = await fetch(this.makeUrl(path, query), { method: 'GET', headers: this.headers() });
    if (!res.ok) throw await this.toError(res);
    return (await res.json()) as T;
  }

  async postJson<T>(path: string, body?: unknown, query?: Query): Promise<T> {
    const res = await fetch(this.makeUrl(path, query), {
      method: 'POST',
      headers: this.headers('application/json'),
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (!res.ok) throw await this.toError(res);
    return (await res.json()) as T;
  }

  async putJson<T>(path: string, body?: unknown, query?: Query): Promise<T> {
    const res = await fetch(this.makeUrl(path, query), {
      method: 'PUT',
      headers: this.headers('application/json'),
      body: body === undefined ? undefined : JSON.stringify(body),
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

  private async toError(res: Response): Promise<ApiError> {
    return res
      .text()
      .then(err => {
        const apiError: ApiError = JSON.parse(err);
        return apiError;
      })
      .catch(() => new ApiError(res.statusText, res.status));
  }
}

// Export a singleton client with defaults; consumers may create their own.
const apiOrigin = import.meta.env.VITE_APP_API_ORIGIN;
export const httpClient = new HttpClient({
  baseUrl: `${apiOrigin.length > 0 ? apiOrigin : (globalThis as WindowProps).__CONTEXT_PATH__}`,
});

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
export type HistoryFilters = {
  id?: string; // connection ID occurrence
  un?: string; // username occurrence
  ch?: string; // client host occurrence
  th?: string; // target host occurrence
  tp?: number[]; // target ports e.g. [80, 443]
  oa?: ISODateTime; // opened after
  ob?: ISODateTime; // opened before
  ca?: ISODateTime; // closed after
  cb?: ISODateTime; // closed before
};
export type HistoryRequestParams = HistoryFilters & {
  sort?: string[]; // sorting criteria e.g. ["targetHost:asc", "openedAt:desc"]
  pg?: number; // page number - default is 0
  sz?: number; // page size - default is 10
};

export const ConnectionApi = {
  // GET /api/connection
  list: async (client: HttpClient = httpClient): Promise<Soht2Connection[]> => {
    return client.getJson<Soht2Connection[]>('/api/connection');
  },

  // GET /api/connection/history with many filters
  history: async (
    filters: HistoryRequestParams = {},
    client: HttpClient = httpClient
  ): Promise<HistoryPage> => {
    return client.getJson<HistoryPage>('/api/connection/history', filters as Query);
  },

  // DELETE /api/connection/{id}
  close: async (id: UUID, client: HttpClient = httpClient): Promise<void> => {
    return client.delete(`/api/connection/${encodeURIComponent(id)}`);
  },
};
