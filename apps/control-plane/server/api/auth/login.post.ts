import { z } from 'zod'
import { defineEventHandler, readBody, createError, setCookie } from 'h3'
import { verifyPassword, issueSession } from '../../utils/auth-service.js'

const LoginSchema = z.object({
  email: z.string().email(),
  password: z.string().min(8),
})

export default defineEventHandler(async (event) => {
  const parsed = LoginSchema.safeParse(await readBody(event))
  if (!parsed.success) {
    throw createError({ statusCode: 400, statusMessage: parsed.error.issues[0]?.message ?? 'Invalid login' })
  }
  const user = await verifyPassword(parsed.data.email, parsed.data.password)
  if (!user) {
    throw createError({ statusCode: 401, statusMessage: 'Invalid email or password' })
  }
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
