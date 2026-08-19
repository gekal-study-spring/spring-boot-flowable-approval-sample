'use client';

import Button from '@mui/material/Button';
import Grid from '@mui/material/Grid';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { approvalRouteLabel } from '@/lib/approval-policy';
import type { ExpenseRequestInput } from '@/lib/api-types';

const CATEGORIES = ['旅費交通費', '会議費', '交際費', '消耗品費', '通信費'];

interface Props {
  onSubmit: (input: ExpenseRequestInput) => Promise<void>;
}

/** 経費精算の申請フォーム。金額に応じてどちらの承認者へ回るかを事前に示す。 */
export function RequestForm({ onSubmit }: Props) {
  const today = new Date().toISOString().slice(0, 10);
  const [title, setTitle] = useState('9月出張旅費');
  const [amount, setAmount] = useState(50000);
  const [expenseDate, setExpenseDate] = useState(today);
  const [category, setCategory] = useState(CATEGORIES[0]);
  const [remarks, setRemarks] = useState('新幹線往復');
  const [submitting, setSubmitting] = useState(false);

  return (
    <Stack
      component="form"
      spacing={2}
      onSubmit={async event => {
        event.preventDefault();
        setSubmitting(true);
        try {
          await onSubmit({
            title,
            amount,
            expenseDate,
            category,
            remarks: remarks.trim() === '' ? null : remarks,
          });
        } finally {
          setSubmitting(false);
        }
      }}
    >
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <TextField
            fullWidth
            size="small"
            label="件名"
            value={title}
            onChange={event => setTitle(event.target.value)}
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <TextField
            fullWidth
            size="small"
            type="number"
            label="申請金額（円）"
            value={amount}
            onChange={event => setAmount(Number(event.target.value))}
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <TextField
            fullWidth
            size="small"
            type="date"
            label="支出日"
            value={expenseDate}
            slotProps={{ inputLabel: { shrink: true }, htmlInput: { max: today } }}
            onChange={event => setExpenseDate(event.target.value)}
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <TextField
            select
            fullWidth
            size="small"
            label="費目"
            value={category}
            onChange={event => setCategory(event.target.value)}
          >
            {CATEGORIES.map(item => (
              <MenuItem key={item} value={item}>
                {item}
              </MenuItem>
            ))}
          </TextField>
        </Grid>
        <Grid size={12}>
          <TextField
            fullWidth
            size="small"
            label="備考"
            value={remarks}
            onChange={event => setRemarks(event.target.value)}
          />
        </Grid>
      </Grid>
      <Stack
        direction="row"
        spacing={2}
        sx={{ alignItems: 'center', justifyContent: 'space-between' }}
      >
        <Typography variant="body2" color="text.secondary">
          この金額は <strong>{approvalRouteLabel(amount)}</strong>{' '}
          へ回ります（10万円以上は部長承認）
        </Typography>
        <Button type="submit" variant="contained" disabled={submitting}>
          {submitting ? '送信中…' : '申請する'}
        </Button>
      </Stack>
    </Stack>
  );
}
