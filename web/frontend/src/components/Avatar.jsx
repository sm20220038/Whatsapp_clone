import { mediaUrl } from '../api';
import { initials } from '../format';

export default function Avatar({ name, avatarId, token, isGroup = false,
                                 online = false, variant = '' }) {
  return (
    <div className={`avatar ${isGroup ? 'group' : ''} ${variant}`}>
      {isGroup ? (
        '#'
      ) : avatarId && token ? (
        <img className="avatar-img" src={mediaUrl(token, avatarId)} alt="" />
      ) : (
        initials(name)
      )}
      {!isGroup && online && <span className="dot" />}
    </div>
  );
}
