export default function LegalPage() {
  return (
    <div className="max-w-4xl mx-auto py-8 px-4">
      <h1 className="text-3xl md:text-4xl font-bold mb-6">Legal Notice</h1>

      <div className="space-y-6 text-slate-700 text-base md:text-lg leading-relaxed">
        <section>
          <h2 className="text-xl md:text-2xl font-semibold mb-3">1. Service Provider Information</h2>
          <p>
            This website is operated by:
          </p>
          <p className="mt-2">
            RaimonVibe<br />
            Timpaan 1-B<br />
            1628 MT Hoorn<br />
            Netherlands<br />
            Email: info@raimonvibe.com
          </p>
        </section>

        <section>
          <h2 className="text-xl md:text-2xl font-semibold mb-3">2. Terms of Service</h2>

          <h3 className="font-semibold mt-4 mb-2">2.1 Service Description</h3>
          <p>
            RaimonVibe provides an online image conversion service that allows users to convert images between
            various formats. The service offers a free tier with 20 conversions per day and a paid subscription
            for $1.98/month providing 1000 conversions.
          </p>

          <h3 className="font-semibold mt-4 mb-2">2.2 User Accounts</h3>
          <p>
            To use our service, you must create an account using a third-party authentication provider.
            You are responsible for maintaining the confidentiality of your account and for all activities
            that occur under your account.
          </p>

          <h3 className="font-semibold mt-4 mb-2">2.3 Acceptable Use</h3>
          <p className="mb-2">You agree not to use our service to:</p>
          <ul className="list-disc pl-6 space-y-2">
            <li>Upload or convert images that contain illegal, harmful, or offensive content</li>
            <li>Violate any copyright, trademark, or other intellectual property rights</li>
            <li>Attempt to circumvent usage limits or security measures</li>
            <li>Interfere with or disrupt the service or servers</li>
            <li>Use automated tools to abuse the service (bot attacks, scraping, etc.)</li>
          </ul>

          <h3 className="font-semibold mt-4 mb-2">2.4 Intellectual Property</h3>
          <p>
            You retain all rights to the images you upload. By using our service, you grant us a temporary license
            to process and convert your images solely for the purpose of providing the conversion service.
            We do not claim ownership of your content.
          </p>

          <h3 className="font-semibold mt-4 mb-2">2.5 Subscription and Payment</h3>
          <p>
            Paid subscriptions are billed monthly at $1.98 per month. Subscriptions automatically renew unless
            canceled before the renewal date. All payments are processed securely through Stripe. Refunds may
            be requested within 14 days of purchase by contacting info@raimonvibe.com
          </p>
        </section>

        <section>
          <h2 className="text-xl md:text-2xl font-semibold mb-3">3. Liability and Warranties</h2>

          <h3 className="font-semibold mt-4 mb-2">3.1 Service Availability</h3>
          <p>
            We strive to provide reliable service but do not guarantee uninterrupted or error-free operation.
            The service is provided &quot;as is&quot; without warranties of any kind, either express or implied.
          </p>

          <h3 className="font-semibold mt-4 mb-2">3.2 Limitation of Liability</h3>
          <p>
            To the maximum extent permitted by law, RaimonVibe shall not be liable for any indirect, incidental,
            special, consequential, or punitive damages, or any loss of profits or revenues, whether incurred
            directly or indirectly, or any loss of data, use, goodwill, or other intangible losses resulting from:
          </p>
          <ul className="list-disc pl-6 space-y-2 mt-2">
            <li>Your use or inability to use the service</li>
            <li>Any unauthorized access to or use of our servers</li>
            <li>Any bugs, viruses, or other harmful code that may be transmitted through our service</li>
            <li>Any errors or omissions in any content or for any loss or damage incurred as a result of the use of any content</li>
          </ul>

          <h3 className="font-semibold mt-4 mb-2">3.3 Data Loss</h3>
          <p>
            While we implement security measures to protect your data, we are not responsible for any data loss.
            Users should maintain backup copies of important images before conversion.
          </p>
        </section>

        <section>
          <h2 className="text-xl md:text-2xl font-semibold mb-3">4. Copyright and Trademarks</h2>
          <p>
            All content on this website, including but not limited to text, graphics, logos, and software,
            is the property of RaimonVibe or its content suppliers and is protected by international copyright laws.
            The compilation of all content on this site is the exclusive property of RaimonVibe.
          </p>
        </section>

        <section>
          <h2 className="text-xl md:text-2xl font-semibold mb-3">5. Third-Party Services</h2>
          <p>
            Our service uses third-party services for authentication (Google, GitHub, etc.) and payment processing (Stripe).
            Your use of these services is subject to their respective terms of service and privacy policies.
            We are not responsible for the practices of these third-party services.
          </p>
        </section>

        <section>
          <h2 className="text-xl md:text-2xl font-semibold mb-3">6. Termination</h2>
          <p>
            We reserve the right to suspend or terminate your account and access to the service at any time,
            without prior notice, for any reason, including but not limited to violation of these terms.
            You may terminate your account at any time by contacting us at info@raimonvibe.com
          </p>
        </section>

        <section>
          <h2 className="text-xl md:text-2xl font-semibold mb-3">7. Modifications to Service and Terms</h2>
          <p>
            We reserve the right to modify or discontinue the service at any time, with or without notice.
            We may also update these terms from time to time. Continued use of the service after changes
            constitutes acceptance of the new terms.
          </p>
        </section>

        <section>
          <h2 className="text-xl md:text-2xl font-semibold mb-3">8. Governing Law and Jurisdiction</h2>
          <p>
            These terms shall be governed by and construed in accordance with the laws of the Netherlands.
            Any disputes arising from these terms or your use of the service shall be subject to the exclusive
            jurisdiction of the courts of the Netherlands.
          </p>
        </section>

        <section>
          <h2 className="text-xl md:text-2xl font-semibold mb-3">9. Severability</h2>
          <p>
            If any provision of these terms is found to be invalid or unenforceable, the remaining provisions
            shall continue in full force and effect.
          </p>
        </section>

        <section>
          <h2 className="text-xl md:text-2xl font-semibold mb-3">10. Contact Information</h2>
          <p>
            For questions about these terms or any legal matters, please contact us at:
          </p>
          <p className="mt-2">
            Email: info@raimonvibe.com<br />
            Address: Timpaan 1-B, 1628 MT Hoorn, Netherlands
          </p>
        </section>

        <section>
          <h2 className="text-xl md:text-2xl font-semibold mb-3">11. Dispute Resolution</h2>
          <p>
            In the event of any dispute arising from your use of our service, we encourage you to first
            contact us directly at info@raimonvibe.com to seek an amicable resolution. If a resolution
            cannot be reached, disputes will be handled in accordance with the jurisdiction specified above.
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
