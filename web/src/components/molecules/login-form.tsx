'use client';

import Button from '@mui/material/Button';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { SAMPLE_PASSWORD, SAMPLE_USERS } from '@/config/site';
import type { Credentials } from '@/lib/api-types';

interface Props {
  onLogin: (credentials: Credentials) => void;
}

/** サンプルユーザーを選んでログインするフォーム。 */
export function LoginForm({ onLogin }: Props) {
  const [username, setUsername] = useState<string>(SAMPLE_USERS[0].username);
  const [password, setPassword] = useState<string>(SAMPLE_PASSWORD);

  return (
    <Stack
      component="form"
      direction={{ xs: 'column', sm: 'row' }}
      spacing={1.5}
      sx={{ alignItems: { sm: 'center' } }}
      onSubmit={event => {
        event.preventDefault();
        onLogin({ username, password });
      }}
    >
      <TextField
        select
        size="small"
        label="ユーザー"
        value={username}
        onChange={event => setUsername(event.target.value)}
        sx={{ minWidth: 200 }}
      >
        {SAMPLE_USERS.map(user => (
          <MenuItem key={user.username} value={user.username}>
            {user.label}
          </MenuItem>
        ))}
      </TextField>
      <TextField
        size="small"
        type="password"
        label="パスワード"
        value={password}
        onChange={event => setPassword(event.target.value)}
      />
      <Button type="submit" variant="contained">
        ログイン
      </Button>
      <Typography variant="body2" color="text.secondary">
        サンプルのパスワードはいずれも {SAMPLE_PASSWORD}
      </Typography>
    </Stack>
  );
}
