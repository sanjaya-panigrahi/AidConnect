import {
  MAX_USERNAME_LENGTH, MAX_NAME_LENGTH, MAX_EMAIL_LENGTH,
  MIN_LOGIN_PASSWORD_LENGTH, MIN_REGISTER_PASSWORD_LENGTH, MAX_PASSWORD_LENGTH,
  MAX_MESSAGE_LENGTH, USERNAME_PATTERN, EMAIL_PATTERN,
  PASSWORD_NO_WHITESPACE_PATTERN, PASSWORD_UPPERCASE_PATTERN,
  PASSWORD_LOWERCASE_PATTERN, PASSWORD_NUMBER_PATTERN, PASSWORD_SPECIAL_PATTERN
} from './constants';

export function validateLoginValues(form) {
  const payload = { username: form.username.trim(), password: form.password };
  const errors = {};

  if (!payload.username) {
    errors.username = 'Username is required.';
  } else if (payload.username.length < 3 || payload.username.length > MAX_USERNAME_LENGTH) {
    errors.username = `Username must be between 3 and ${MAX_USERNAME_LENGTH} characters.`;
  } else if (!USERNAME_PATTERN.test(payload.username)) {
    errors.username = 'Username can contain letters, numbers, dots, underscores, and hyphens only.';
  }

  if (!payload.password.trim()) {
    errors.password = 'Password is required.';
  } else if (payload.password.length < MIN_LOGIN_PASSWORD_LENGTH || payload.password.length > MAX_PASSWORD_LENGTH) {
    errors.password = `Password must be between ${MIN_LOGIN_PASSWORD_LENGTH} and ${MAX_PASSWORD_LENGTH} characters.`;
  }

  return { payload, errors };
}

export function validateRegisterValues(form) {
  const payload = {
    firstName: form.firstName.trim(),
    lastName:  form.lastName.trim(),
    username:  form.username.trim(),
    password:  form.password,
    email:     form.email.trim()
  };
  const errors = {};
  const { password } = payload;

  if (!payload.firstName) { errors.firstName = 'First name is required.'; }
  else if (payload.firstName.length > MAX_NAME_LENGTH) { errors.firstName = `First name must be ${MAX_NAME_LENGTH} characters or fewer.`; }

  if (!payload.lastName) { errors.lastName = 'Last name is required.'; }
  else if (payload.lastName.length > MAX_NAME_LENGTH) { errors.lastName = `Last name must be ${MAX_NAME_LENGTH} characters or fewer.`; }

  if (!payload.username) { errors.username = 'Username is required.'; }
  else if (payload.username.length < 3 || payload.username.length > MAX_USERNAME_LENGTH) { errors.username = `Username must be between 3 and ${MAX_USERNAME_LENGTH} characters.`; }
  else if (!USERNAME_PATTERN.test(payload.username)) { errors.username = 'Username can contain letters, numbers, dots, underscores, and hyphens only.'; }

  if (!payload.email) { errors.email = 'Email is required.'; }
  else if (payload.email.length > MAX_EMAIL_LENGTH) { errors.email = `Email must be ${MAX_EMAIL_LENGTH} characters or fewer.`; }
  else if (!EMAIL_PATTERN.test(payload.email)) { errors.email = 'Email must be a valid email address.'; }

  if (!password.trim()) { errors.password = 'Password is required.'; }
  else if (password.length < MIN_REGISTER_PASSWORD_LENGTH || password.length > MAX_PASSWORD_LENGTH) { errors.password = `Password must be between ${MIN_REGISTER_PASSWORD_LENGTH} and ${MAX_PASSWORD_LENGTH} characters.`; }
  else if (!PASSWORD_NO_WHITESPACE_PATTERN.test(password)) { errors.password = 'Password cannot contain spaces.'; }
  else if (
    !PASSWORD_UPPERCASE_PATTERN.test(password) ||
    !PASSWORD_LOWERCASE_PATTERN.test(password) ||
    !PASSWORD_NUMBER_PATTERN.test(password) ||
    !PASSWORD_SPECIAL_PATTERN.test(password)
  ) { errors.password = 'Password must include uppercase, lowercase, number, and special character.'; }

  if (!form.confirmPassword.trim()) { errors.confirmPassword = 'Please confirm your password.'; }
  else if (form.confirmPassword !== password) { errors.confirmPassword = 'Passwords do not match.'; }

  return { payload, errors };
}

export function validateMessageContent(text) {
  if (!text.trim()) return 'Message cannot be empty.';
  if (text.length > MAX_MESSAGE_LENGTH) return `Message content must be ${MAX_MESSAGE_LENGTH} characters or fewer.`;
  return '';
}

export function getPasswordChecks(password) {
  return {
    minLength:    password.length >= MIN_REGISTER_PASSWORD_LENGTH,
    upper:        PASSWORD_UPPERCASE_PATTERN.test(password),
    lower:        PASSWORD_LOWERCASE_PATTERN.test(password),
    number:       PASSWORD_NUMBER_PATTERN.test(password),
    special:      PASSWORD_SPECIAL_PATTERN.test(password),
    noWhitespace: password.length > 0 ? PASSWORD_NO_WHITESPACE_PATTERN.test(password) : false
  };
}

export function getPasswordStrength(password) {
  if (!password) return { label: 'Enter a password', level: 'none' };
  const score = Object.values(getPasswordChecks(password)).filter(Boolean).length;
  if (score <= 2) return { label: 'Weak password',   level: 'weak'   };
  if (score <= 4) return { label: 'Medium password', level: 'medium' };
  return            { label: 'Strong password', level: 'strong' };
}
