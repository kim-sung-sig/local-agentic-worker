import { z } from 'zod'
import { defineEventHandler, readBody, createError, setCookie } from 'h3'
import { registerUser, issueSession } from '../../utils/auth-service.js'

const RegisterSchema = z.object({
  email: z.string().email(),
  password: z.string().min(8),
  name: z.string().min(1).optional(),
})

export default defineEventHandler(async (event) => {
  const parsed = RegisterSchema.safeParse(await readBody(event))
  if (!parsed.success) {
    throw createError({ statusCode: 400, statusMessage: parsed.error.issues[0]?.message ?? 'Invalid registration' })
  }
  const user = await registerUser(parsed.data)
  const session = await issueSession(user.id)
  setCookie(event, 'session_token', session.rawToken, {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax',
    expires: session.expiresAt,
    path: '/',
  })
  return user
})
