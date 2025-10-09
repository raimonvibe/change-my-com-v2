export default function PrivacyPage() {
  return (
    <div className="max-w-4xl mx-auto py-8 px-4">
      <h1 className="text-3xl font-bold mb-6">Privacy Notice</h1>

      <div className="space-y-6 text-slate-700">
        <section>
          <h2 className="text-xl font-semibold mb-3">1. Data Controller</h2>
          <p>
            RaimonVibe<br />
            Timpaan 1-B<br />
            1628 MT Hoorn<br />
            Netherlands<br />
            Email: info@raimonvibe.com
          </p>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-3">2. Data Collection and Processing</h2>
          <p className="mb-3">
            Our image converter application processes the following data:
          </p>
          <ul className="list-disc pl-6 space-y-2">
            <li><strong>Account Information:</strong> When you create an account, we collect your email address and authentication credentials provided through third-party authentication services (Google, GitHub, etc.).</li>
            <li><strong>Image Files:</strong> Images you upload for conversion are temporarily processed on our servers and are automatically deleted after conversion is complete.</li>
            <li><strong>Usage Data:</strong> We track the number of conversions performed to enforce daily limits and subscription quotas.</li>
            <li><strong>Payment Information:</strong> If you subscribe to our paid plan, payment processing is handled securely by Stripe. We do not store your credit card details.</li>
            <li><strong>Contact Form:</strong> When you submit our contact form, your name, email address, and message are processed by Formspree (a third-party service) and forwarded to us via email. We use this information solely to respond to your inquiry.</li>
          </ul>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-3">3. Purpose of Data Processing</h2>
          <p className="mb-2">We process your data for the following purposes:</p>
          <ul className="list-disc pl-6 space-y-2">
            <li>To provide image conversion services</li>
            <li>To manage your account and authentication</li>
            <li>To process payments and manage subscriptions</li>
            <li>To enforce usage limits and prevent abuse</li>
            <li>To respond to your inquiries and provide customer support</li>
            <li>To improve our service quality</li>
          </ul>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-3">4. Analytics and Tracking</h2>
          <p>
            We do not use any third-party analytics tools, cookies, or tracking technologies. Your browsing behavior is not monitored or analyzed.
          </p>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-3">5. Data Storage and Security</h2>
          <p className="mb-2">
            We implement appropriate technical and organizational measures to protect your data:
          </p>
          <ul className="list-disc pl-6 space-y-2">
            <li>Uploaded images are stored temporarily and deleted immediately after conversion</li>
            <li>Account data is stored securely on our servers</li>
            <li>All data transmission occurs over encrypted HTTPS connections</li>
            <li>Authentication is handled through secure third-party providers</li>
          </ul>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-3">6. Data Sharing</h2>
          <p className="mb-2">We share your data only with the following service providers:</p>
          <ul className="list-disc pl-6 space-y-2">
            <li><strong>Authentication Providers:</strong> Google, GitHub, or other OAuth providers you choose to authenticate with</li>
            <li><strong>Payment Processor:</strong> Stripe for subscription payment processing</li>
            <li><strong>Contact Form Service:</strong> Formspree for processing and forwarding contact form submissions</li>
          </ul>
          <p className="mt-3">
            We do not sell, rent, or share your personal data with any other third parties for marketing purposes.
          </p>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-3">7. Your Rights (GDPR)</h2>
          <p className="mb-2">Under the General Data Protection Regulation (GDPR), you have the following rights:</p>
          <ul className="list-disc pl-6 space-y-2">
            <li><strong>Right to Access:</strong> Request a copy of the personal data we hold about you</li>
            <li><strong>Right to Rectification:</strong> Request correction of inaccurate personal data</li>
            <li><strong>Right to Erasure:</strong> Request deletion of your personal data</li>
            <li><strong>Right to Restriction:</strong> Request restriction of processing of your personal data</li>
            <li><strong>Right to Data Portability:</strong> Receive your personal data in a structured, machine-readable format</li>
            <li><strong>Right to Object:</strong> Object to processing of your personal data</li>
            <li><strong>Right to Withdraw Consent:</strong> Withdraw consent at any time where processing is based on consent</li>
          </ul>
          <p className="mt-3">
            To exercise any of these rights, please contact us at info@raimonvibe.com
          </p>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-3">8. Data Retention</h2>
          <p>
            We retain your account information for as long as your account is active. If you delete your account,
            we will delete your personal data within 30 days, except where we are required to retain it for legal purposes.
            Uploaded images are deleted immediately after conversion.
          </p>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-3">9. International Data Transfers</h2>
          <p>
            Your data may be transferred to and processed in countries outside the European Economic Area (EEA).
            We ensure that appropriate safeguards are in place to protect your data in accordance with GDPR requirements.
          </p>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-3">10. Children&apos;s Privacy</h2>
          <p>
            Our service is not directed to individuals under the age of 16. We do not knowingly collect personal
            information from children under 16. If you become aware that a child has provided us with personal data,
            please contact us at info@raimonvibe.com
          </p>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-3">11. Changes to This Privacy Notice</h2>
          <p>
            We may update this Privacy Notice from time to time. We will notify you of any changes by posting
            the new Privacy Notice on this page and updating the &quot;Last Updated&quot; date below.
          </p>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-3">12. Contact Us</h2>
          <p>
            If you have any questions about this Privacy Notice or our data practices, please contact us at:
          </p>
          <p className="mt-2">
            Email: info@raimonvibe.com<br />
            Address: Timpaan 1-B, 1628 MT Hoorn, Netherlands
          </p>
        </section>

        <section className="pt-6 border-t border-slate-200">
          <p className="text-sm text-slate-600">
            <strong>Last Updated:</strong> {new Date().toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' })}
          </p>
        </section>
      </div>
    </div>
  );
}
