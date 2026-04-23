'use strict';

const express = require('express');
const { PKPass } = require('passkit-generator');
const apn = require('apn');
const path = require('path');
const fs = require('fs');

const app = express();
app.use(express.json());

const PORT = process.env.PORT || 3001;
const PASS_TYPE_ID = process.env.APPLE_PASS_TYPE_IDENTIFIER || 'pass.placeholder';
const TEAM_ID = process.env.APPLE_TEAM_IDENTIFIER || 'placeholder';
const BASE_URL = process.env.BASE_URL || 'http://localhost:8080';

// APNs provider is lazily initialized so the service starts even without certs
let apnProvider = null;

function getApnProvider() {
    if (apnProvider) return apnProvider;

    const keyPath = path.join(__dirname, 'certs', 'AuthKey.p8');
    if (!fs.existsSync(keyPath)) {
        throw new Error('APNs auth key not found at certs/AuthKey.p8');
    }

    apnProvider = new apn.Provider({
        token: {
            key: keyPath,
            keyId: process.env.APNS_KEY_ID,
            teamId: TEAM_ID,
        },
        production: process.env.NODE_ENV === 'production',
    });

    return apnProvider;
}

function hexToRgb(hex) {
    if (!hex || !/^#[0-9A-Fa-f]{6}$/.test(hex)) return 'rgb(0, 0, 0)';
    const r = parseInt(hex.slice(1, 3), 16);
    const g = parseInt(hex.slice(3, 5), 16);
    const b = parseInt(hex.slice(5, 7), 16);
    return `rgb(${r}, ${g}, ${b})`;
}

/**
 * POST /generate
 * Body: { shopName, logoUrl, primaryColor, stampCount, stampGoal, serialNumber, rewardDescription }
 * Returns: .pkpass binary
 */
app.post('/generate', async (req, res) => {
    const {
        shopName,
        logoUrl,
        primaryColor,
        stampCount,
        stampGoal,
        serialNumber,
        rewardDescription,
    } = req.body;

    if (!serialNumber) {
        return res.status(400).json({ error: 'serialNumber is required' });
    }

    try {
        const certsDir = path.join(__dirname, 'certs');
        const wwdr = path.join(certsDir, 'wwdr.pem');
        const signerCert = path.join(certsDir, 'signerCert.pem');
        const signerKey = path.join(certsDir, 'signerKey.pem');

        const pass = await PKPass.from(
            {
                model: path.join(__dirname, 'model'),
                certificates: {
                    wwdr,
                    signerCert,
                    signerKey,
                    signerKeyPassphrase: process.env.CERT_PASSPHRASE || '',
                },
            },
            {
                // These override the template values in model/pass.json
                serialNumber,
                description: `${shopName || 'Loyalty'} Card`,
                organizationName: shopName || 'StampX Shop',
                passTypeIdentifier: PASS_TYPE_ID,
                teamIdentifier: TEAM_ID,
                foregroundColor: 'rgb(255, 255, 255)',
                backgroundColor: hexToRgb(primaryColor),
                labelColor: 'rgb(220, 220, 220)',
                logoText: shopName || '',
                storeCard: {
                    primaryFields: [
                        {
                            key: 'stamps',
                            label: 'Stamps',
                            value: `${stampCount}/${stampGoal}`,
                            textAlignment: 'PKTextAlignmentCenter',
                        },
                    ],
                    secondaryFields: [
                        {
                            key: 'reward',
                            label: 'Reward',
                            value: rewardDescription || '',
                        },
                    ],
                },
                // Web service URL tells Apple Wallet where to register and fetch updates
                webServiceURL: `${BASE_URL}/v1/`,
                // authenticationToken = serialNumber for simplicity; validate in production
                authenticationToken: serialNumber,
                barcode: {
                    format: 'PKBarcodeFormatQR',
                    message: serialNumber,
                    messageEncoding: 'iso-8859-1',
                },
            }
        );

        const buffer = pass.getAsBuffer();
        res.set('Content-Type', 'application/vnd.apple.pkpass');
        res.set('Content-Disposition', `attachment; filename="${serialNumber}.pkpass"`);
        res.send(buffer);
    } catch (err) {
        console.error('[generate] Error:', err.message);
        res.status(500).json({ error: err.message });
    }
});

/**
 * POST /push
 * Body: { pushToken, passTypeIdentifier }
 * Sends a silent APNs push so Apple Wallet fetches the updated pass.
 */
app.post('/push', async (req, res) => {
    const { pushToken, passTypeIdentifier } = req.body;

    if (!pushToken) {
        return res.status(400).json({ error: 'pushToken is required' });
    }

    try {
        const provider = getApnProvider();
        const note = new apn.Notification();
        note.topic = passTypeIdentifier || PASS_TYPE_ID;
        note.payload = {}; // empty payload — Apple Wallet triggers on any push to the pass topic

        const result = await provider.send(note, pushToken);
        res.json({ sent: result.sent.length, failed: result.failed.length });
    } catch (err) {
        console.error('[push] Error:', err.message);
        res.status(500).json({ error: err.message });
    }
});

app.get('/health', (_req, res) => res.json({ status: 'ok' }));

app.listen(PORT, () => {
    console.log(`pass-service running on port ${PORT}`);
    console.log(`PASS_TYPE_ID: ${PASS_TYPE_ID}`);
    console.log(`Certs directory: ${path.join(__dirname, 'certs')}`);
});
