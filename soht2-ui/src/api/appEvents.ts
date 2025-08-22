/* SOHT2 © Licensed under MIT 2025. */
import type { ApiError, UUID } from './soht2Api';

export class AppErrorEvent extends CustomEvent<ApiError> {
  public static readonly TYPE: string = 'app:error';

  constructor(error: ApiError) {
    super(AppErrorEvent.TYPE, { detail: error });
  }
}
export function dispatchAppErrorEvent(error: ApiError, element: EventTarget = window) {
  element.dispatchEvent(new AppErrorEvent(error));
}

export type UserChangedAction = 'create' | 'delete' | 'update';
export class UserChangedEvent extends CustomEvent<{ action: UserChangedAction; username: string }> {
  public static readonly TYPE: string = 'user:changed';

  constructor(action: UserChangedAction, username: string) {
    super(UserChangedEvent.TYPE, { detail: { action, username } });
  }
}
export function dispatchUserChangedEvent(
  action: UserChangedAction,
  username: string,
  element: EventTarget = window
) {
  element.dispatchEvent(new UserChangedEvent(action, username));
}

export type ConnectionChangedAction = 'close';
export class ConnectionChangedEvent extends CustomEvent<{
  action: ConnectionChangedAction;
  connectionId: UUID;
}> {
  public static readonly TYPE: string = 'connection:changed';

  constructor(action: ConnectionChangedAction, connectionId: UUID) {
    super(ConnectionChangedEvent.TYPE, { detail: { action, connectionId } });
  }
}
export function dispatchConnectionChangedEvent(
  action: ConnectionChangedAction,
  connectionId: UUID,
  element: EventTarget = window
) {
  element.dispatchEvent(new ConnectionChangedEvent(action, connectionId));
}
