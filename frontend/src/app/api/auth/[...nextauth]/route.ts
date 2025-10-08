import NextAuth from "next-auth";
import Google from "next-auth/providers/google";
import type { JWT } from "next-auth/jwt";
import type { Session } from "next-auth";
import type { Account } from "next-auth";

// Extend the default types
declare module "next-auth" {
  interface Session {
    idToken?: string;
  }
}

declare module "next-auth/jwt" {
  interface JWT {
    idToken?: string;
  }
}

const handler = NextAuth({
  providers: [
    Google({
      clientId: process.env.GOOGLE_CLIENT_ID || "",
      clientSecret: process.env.GOOGLE_CLIENT_SECRET || "",
    }),
  ],
  session: { strategy: "jwt" },
  callbacks: {
    async jwt({ token, account }: { token: JWT; account: Account | null }) {
      if (account && account.id_token) {
        token.idToken = account.id_token;
      }
      return token;
    },
    async session({ session, token }: { session: Session; token: JWT }) {
      session.idToken = token.idToken;
      return session;
    },
  },
});

export { handler as GET, handler as POST };
