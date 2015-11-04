package org.baobab.foodcoapp;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class TransactionFragment extends Fragment
        implements LoaderManager.LoaderCallbacks<Cursor> {

    private TransactionView transaction;
    private ScrollView scrollView;

    @Override
    public View onCreateView(LayoutInflater flate, ViewGroup p, Bundle state) {
        scrollView = (ScrollView) flate.inflate(R.layout.fragment_transaction, null, false);
        transaction = (TransactionView) scrollView.findViewById(R.id.transaction);
        transaction.setOnAmountClick(new NumberEditListener() {
            @Override
            String text(Cursor product) {
                return "Wie viel " + product.getString(3) + " " + product.getString(1);
            }

            @Override
            int inputType(Cursor product) {
                if (!product.isNull(3) && product.getString(3).equals(getString(R.string.piece))) {
                    return InputType.TYPE_CLASS_NUMBER;
                } else {
                    return InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
                }
            }

            @Override
            float quantity(float number, Cursor product) {
                return number;
            }
        });
        transaction.setOnSumClick(new NumberEditListener() {
            @Override
            String text(Cursor product) {
                return product.getString(1) + " für wie viel Cash?";
            }

            @Override
            int inputType(Cursor product) {
                return InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
            }

            @Override
            float quantity(float number, Cursor product) {
                if (!product.isNull(3) && product.getString(3).equals(getString(R.string.piece))) {
                    return (int) (number / product.getFloat(2));
                } else {
                    return number / product.getFloat(2);
                }
            }
        });
        return scrollView;
    }

    abstract class NumberEditListener implements View.OnClickListener {

        abstract String text(Cursor product);

        abstract int inputType(Cursor product);

        abstract float quantity(float number, Cursor product);

        @Override
        public void onClick(final View v) {
            final Cursor c = getActivity().getContentResolver().query(
                    Uri.parse("content://org.baobab.foodcoapp/products/" + v.getId()),
                    null, null, null, null);
            int inputType = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
            String text = "Wie viel?";
            if (c.getCount() > 0) {
                c.moveToFirst();
                text = text(c);
                inputType = inputType(c);
            }
            getFragmentManager().beginTransaction()
                    .replace(R.id.container, new NumberDialogFragment(
                            text, (String) v.getTag(), inputType) {
                        @Override
                        public void onNumber(float number) {
                            ContentValues cv = new ContentValues();
                            if (c.getCount() > 0) {
                                cv.put("quantity", quantity(number, c));
                            } else {
                                cv.put("quantity", number);
                            }
                            getActivity().getContentResolver()
                                    .update(getActivity().getIntent().getData().buildUpon()
                                            .appendEncodedPath("products/" + v.getId())
                                            .build(), cv, null, null);
                        }
                    }, "edit")
                    .addToBackStack("amount").commitAllowingStateLoss();
        }
    }
    public void load() {
        getActivity().getSupportLoaderManager().restartLoader(1, null, this);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(getActivity(),
                getActivity().getIntent().getData(),
                null, null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (getFragmentManager().getBackStackEntryCount() > 0) {
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    getFragmentManager().popBackStack();
                }
            });
            ((InputMethodManager) getActivity()
                    .getSystemService(Context.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(getView().getWindowToken(), 0);
        }
        transaction.populate(data);
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
        Cursor s = getActivity().getContentResolver().query(
                getActivity().getIntent().getData().buildUpon()
                        .appendEncodedPath("sum").build(), null, null, null, null);
        s.moveToFirst();
        final float sum = - s.getFloat(2);
        TextView ok = ((TextView) ((FragmentActivity) getActivity())
                .findViewById(R.id.sum));
        TextView header = ((TextView) ((FragmentActivity) getActivity())
                .findViewById(R.id.header));
        if (sum < 0.01 && sum > -0.01) {
            header.setText("");
            header.setBackgroundResource(R.drawable.background_translucent);
            ok.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_ok, 0, 0, 0);
            ok.setText("");
        } else {
            ok.setCompoundDrawablesWithIntrinsicBounds(0,0,0,0);
            ok.setText(String.format("%.2f", sum));
            if (sum < 0) {
                header.setText("Wechselgeld");
                header.setBackgroundResource(R.drawable.background_red);
            } else {
                header.setText("zu Bezahlen");
                header.setTextColor(getResources().getColor(R.color.medium_green));
                header.setBackgroundResource(R.drawable.background_green);
            }
        }
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sum < -0.01) {
                    MediaPlayer.create(getActivity(), R.raw.error_3).start();
                    Toast.makeText(getActivity(), "Wechselgeld " + sum, Toast.LENGTH_SHORT).show();
                } else if (sum > 0.01) {
                    MediaPlayer.create(getActivity(), R.raw.error_4).start();
                    Toast.makeText(getActivity(), "Noch " + sum + " offen!", Toast.LENGTH_LONG).show();
                } else {
                    Cursor t = getActivity().getContentResolver().query(
                            getActivity().getIntent().getData(), null, null, null, null);
                    String msg = "";
                    if (t.getCount() == 0) return;
                    for (int i = 0; i < t.getCount(); i++) {
                        t.moveToPosition(i);
                        Cursor stocks = getActivity().getContentResolver().query(
                                Uri.parse("content://org.baobab.foodcoapp/accounts/" + t.getString(2) + "/products"),
                                null, "title IS '" + t.getString(7) + "'", null, null);
                        int factor = 1;
                        if (t.getString(10).equals("passiva")) {
                            factor = -1;
                        }
                        if (stocks.getCount() > 0) {
                            stocks.moveToFirst();
                            if (factor * stocks.getInt(4) >= 0 && factor * stocks.getInt(4) + factor * t.getInt(4) < 0) {
                                msg += " - Nicht genug " + t.getString(7) + " auf " + t.getString(12) + "\n";
                            }
                        } else if (factor * t.getInt(4) < 0) {
                            msg += " - Nicht genug " + t.getString(7) + " auf " + t.getString(12) + "\n";
                        }
                    }
                    if (msg.length() > 0) {
                        if (PreferenceManager.getDefaultSharedPreferences(getActivity())
                                .getBoolean("allow_negative_stocks", false)) {
                            msg = "Wirklich ins Minus buchen?\n\n" + msg;
                            new AlertDialog.Builder(getActivity())
                                    .setMessage(msg)
                                    .setPositiveButton("Ja, Genau!", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            MediaPlayer.create(getActivity(), R.raw.yay).start();
                                            MediaPlayer.create(getActivity(), R.raw.chaching).start();
                                            saveStatus("final");
                                            Toast.makeText(getActivity(), "Verbucht :-)", Toast.LENGTH_SHORT).show();
                                            ((PosActivity) getActivity()).resetTransaction();
                                            load();
                                        }
                                    }).setNegativeButton("Abbrechen", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            }).show();
                            Toast.makeText(getActivity(), msg, Toast.LENGTH_LONG).show();
                            MediaPlayer.create(getActivity(), R.raw.error_1).start();
                            return;
                        } else {
                            msg = "Keine Buchung ins Minus erlaubt!\n\n" + msg;
                            Toast.makeText(getActivity(), msg, Toast.LENGTH_LONG).show();
                            MediaPlayer.create(getActivity(), R.raw.error_2).start();
                        }
                    } else {
                        MediaPlayer.create(getActivity(), R.raw.chaching).start();
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                MediaPlayer.create(getActivity(), R.raw.yay).start();
                            }
                        }, 700);

                        saveStatus("final");
                        Toast.makeText(getActivity(), "Verbucht :-)", Toast.LENGTH_SHORT).show();
                        ((PosActivity) getActivity()).resetTransaction();
                        load();
                    }
                }
            }
        });
    }

    private void saveStatus(String status) {
        ContentValues cv = new ContentValues();
        cv.put("status", status);
        cv.put("stop", System.currentTimeMillis());
        getActivity().getContentResolver().update(
                getActivity().getIntent().getData(), cv, null, null);
    }

    private String emptyStocks(String account) {
        Cursor empty_stocks = getActivity().getContentResolver().query(
                Uri.parse("content://org.baobab.foodcoapp/accounts/" + account + "/products"),
                null, "stock < 0", null, null);
        System.out.println("empty " + empty_stocks.getCount());
        String msg = "";
        for (int i = 0; i < empty_stocks.getCount(); i++) {
            empty_stocks.moveToPosition(i);
            msg += " - nicht genug " + empty_stocks.getString(7) + " in " + empty_stocks.getString(2) + "\n";
        }
        return msg;
    }


    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }

}
