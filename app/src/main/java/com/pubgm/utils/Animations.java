package com.pubgm.utils;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ArgbEvaluator;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.animation.AlphaAnimation;
import android.view.animation.ScaleAnimation;
import android.view.animation.RotateAnimation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.BounceInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.AnticipateOvershootInterpolator;
import android.view.animation.CycleInterpolator;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;
import android.view.animation.Interpolator;
import android.os.Build;

public class Animations {
    
    public static final int DURATION_SHORT = 150;
    public static final int DURATION_MEDIUM = 250;
    public static final int DURATION_LONG = 350;
    
    public static final DecelerateInterpolator DECELERATE = new DecelerateInterpolator(2.0f);
    public static final AccelerateInterpolator ACCELERATE = new AccelerateInterpolator(2.0f);
    public static final BounceInterpolator BOUNCE = new BounceInterpolator();
    public static final OvershootInterpolator OVERSHOOT = new OvershootInterpolator(2.0f);
    
    private static final AnticipateOvershootInterpolator ANTICIPATE_OVERSHOOT = new AnticipateOvershootInterpolator(2.0f);
    
    public static void fadeIn(View view, int duration) {
        if (view == null) return;
        if (view.getVisibility() != View.VISIBLE) {
            view.setAlpha(0f);
            view.setScaleX(0.95f);
            view.setScaleY(0.95f);
            view.setVisibility(View.VISIBLE);
            view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(duration)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .start();
        }
    }
    
    public static void fadeOut(View view, int duration) {
        if (view == null) return;
        if (view.getVisibility() == View.VISIBLE) {
            view.animate()
                .alpha(0f)
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(duration)
                .setInterpolator(new AccelerateInterpolator(1.5f))
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        view.setVisibility(View.GONE);
                        view.setAlpha(1f);
                        view.setScaleX(1f);
                        view.setScaleY(1f);
                    }
                })
                .start();
        }
    }
    
    public static void crossFade(View viewOut, View viewIn, int duration) {
        if (viewOut == null || viewIn == null) return;
        fadeOut(viewOut, duration);
        viewIn.setAlpha(0f);
        viewIn.setScaleX(0.9f);
        viewIn.setScaleY(0.9f);
        viewIn.setVisibility(View.VISIBLE);
        viewIn.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(duration)
            .setInterpolator(DECELERATE)
            .start();
    }
    
    public static void slideInRight(View view, int duration) {
        if (view == null) return;
        view.setTranslationX(view.getWidth() * 1.2f);
        view.setAlpha(0.5f);
        view.setVisibility(View.VISIBLE);
        view.animate()
            .translationX(0)
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(new OvershootInterpolator(1.2f))
            .start();
    }
    
    public static void slideOutRight(View view, int duration) {
        if (view == null) return;
        view.animate()
            .translationX(view.getWidth() * 1.2f)
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(new AccelerateInterpolator(1.5f))
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    view.setVisibility(View.GONE);
                    view.setTranslationX(0);
                    view.setAlpha(1f);
                }
            })
            .start();
    }
    
    public static void slideInLeft(View view, int duration) {
        if (view == null) return;
        view.setTranslationX(-view.getWidth() * 1.2f);
        view.setAlpha(0.5f);
        view.setVisibility(View.VISIBLE);
        view.animate()
            .translationX(0)
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(new OvershootInterpolator(1.2f))
            .start();
    }
    
    public static void slideOutLeft(View view, int duration) {
        if (view == null) return;
        view.animate()
            .translationX(-view.getWidth() * 1.2f)
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(new AccelerateInterpolator(1.5f))
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    view.setVisibility(View.GONE);
                    view.setTranslationX(0);
                    view.setAlpha(1f);
                }
            })
            .start();
    }
    
    public static void slideInBottom(View view, int duration) {
        if (view == null) return;
        view.setTranslationY(view.getHeight() * 1.2f);
        view.setAlpha(0.5f);
        view.setVisibility(View.VISIBLE);
        view.animate()
            .translationY(0)
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(new OvershootInterpolator(1.2f))
            .start();
    }
    
    public static void slideOutBottom(View view, int duration) {
        if (view == null) return;
        view.animate()
            .translationY(view.getHeight() * 1.2f)
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(new AccelerateInterpolator(1.5f))
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    view.setVisibility(View.GONE);
                    view.setTranslationY(0);
                    view.setAlpha(1f);
                }
            })
            .start();
    }
    
    public static void slideInTop(View view, int duration) {
        if (view == null) return;
        view.setTranslationY(-view.getHeight() * 1.2f);
        view.setAlpha(0.5f);
        view.setVisibility(View.VISIBLE);
        view.animate()
            .translationY(0)
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(new OvershootInterpolator(1.2f))
            .start();
    }
    
    public static void slideOutTop(View view, int duration) {
        if (view == null) return;
        view.animate()
            .translationY(-view.getHeight() * 1.2f)
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(new AccelerateInterpolator(1.5f))
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    view.setVisibility(View.GONE);
                    view.setTranslationY(0);
                    view.setAlpha(1f);
                }
            })
            .start();
    }
    
    public static void animateClick(View view) {
        if (view == null) return;
        view.animate()
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(80)
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(120)
                        .setInterpolator(new OvershootInterpolator(1.5f))
                        .start();
                }
            })
            .start();
    }
    
    public static void animateClickBounce(View view) {
        if (view == null) return;
        view.animate()
            .scaleX(0.85f)
            .scaleY(0.85f)
            .setDuration(80)
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    view.animate()
                        .scaleX(1.1f)
                        .scaleY(1.1f)
                        .setDuration(150)
                        .setInterpolator(new DecelerateInterpolator())
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                view.animate()
                                    .scaleX(0.98f)
                                    .scaleY(0.98f)
                                    .setDuration(100)
                                    .withEndAction(new Runnable() {
                                        @Override
                                        public void run() {
                                            view.animate()
                                                .scaleX(1f)
                                                .scaleY(1f)
                                                .setDuration(80)
                                                .start();
                                        }
                                    })
                                    .start();
                            }
                        })
                        .start();
                }
            })
            .start();
    }
    
    public static void animateClickOvershoot(View view) {
        if (view == null) return;
        view.animate()
            .scaleX(0.75f)
            .scaleY(0.75f)
            .setDuration(80)
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    view.animate()
                        .scaleX(1.15f)
                        .scaleY(1.15f)
                        .setDuration(150)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                view.animate()
                                    .scaleX(0.95f)
                                    .scaleY(0.95f)
                                    .setDuration(100)
                                    .withEndAction(new Runnable() {
                                        @Override
                                        public void run() {
                                            view.animate()
                                                .scaleX(1f)
                                                .scaleY(1f)
                                                .setDuration(80)
                                                .start();
                                        }
                                    })
                                    .start();
                            }
                        })
                        .start();
                }
            })
            .start();
    }
    
    public static void rippleEffect(View view) {
        if (view == null) return;
        view.animate()
            .scaleX(1.2f)
            .scaleY(1.2f)
            .alpha(0.7f)
            .setDuration(150)
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(200)
                        .setInterpolator(new DecelerateInterpolator())
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                view.animate()
                                    .scaleX(1.05f)
                                    .scaleY(1.05f)
                                    .setDuration(100)
                                    .withEndAction(new Runnable() {
                                        @Override
                                        public void run() {
                                            view.animate()
                                                .scaleX(1f)
                                                .scaleY(1f)
                                                .setDuration(100)
                                                .start();
                                        }
                                    })
                                    .start();
                            }
                        })
                        .start();
                }
            })
            .start();
    }
    
    public static void animateTextColor(TextView textView, int fromColor, int toColor, int duration) {
        if (textView == null) return;
        ValueAnimator colorAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), fromColor, toColor);
        colorAnimator.setDuration(duration);
        colorAnimator.setInterpolator(new DecelerateInterpolator());
        colorAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                textView.setTextColor((int) animator.getAnimatedValue());
            }
        });
        colorAnimator.start();
    }
    
    public static void animateTextSize(TextView textView, float fromSize, float toSize, int duration) {
        if (textView == null) return;
        ValueAnimator animator = ValueAnimator.ofFloat(fromSize, toSize);
        animator.setDuration(duration);
        animator.setInterpolator(new OvershootInterpolator(1.2f));
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float animatedValue = (float) valueAnimator.getAnimatedValue();
                textView.setTextSize(animatedValue);
            }
        });
        animator.start();
    }
    
    public static void shakeText(TextView textView) {
        if (textView == null) return;
        ObjectAnimator shake = ObjectAnimator.ofFloat(
            textView, 
            "translationX", 
            0, 30, -30, 25, -25, 20, -20, 15, -15, 10, -10, 5, -5, 0
        );
        shake.setDuration(600);
        shake.setInterpolator(new DecelerateInterpolator());
        shake.start();
        
        textView.animate()
            .alpha(0.7f)
            .setDuration(200)
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    textView.animate()
                        .alpha(1f)
                        .setDuration(200)
                        .start();
                }
            })
            .start();
    }
    
    public static void animateBackgroundColor(View view, int fromColor, int toColor, int duration) {
        if (view == null) return;
        ValueAnimator colorAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), fromColor, toColor);
        colorAnimator.setDuration(duration);
        colorAnimator.setInterpolator(new DecelerateInterpolator());
        colorAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                view.setBackgroundColor((int) animator.getAnimatedValue());
            }
        });
        colorAnimator.start();
    }
    
    public static void animateGradientColor(GradientDrawable drawable, int fromColor, int toColor, int duration) {
        if (drawable == null) return;
        ValueAnimator colorAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), fromColor, toColor);
        colorAnimator.setDuration(duration);
        colorAnimator.setInterpolator(new DecelerateInterpolator());
        colorAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                drawable.setColor((int) animator.getAnimatedValue());
            }
        });
        colorAnimator.start();
    }
    
    public static void rotateContinuous(View view, int duration) {
        if (view == null) return;
        ObjectAnimator rotation = ObjectAnimator.ofFloat(view, "rotation", 0f, 360f);
        rotation.setDuration(duration);
        rotation.setRepeatCount(ObjectAnimator.INFINITE);
        rotation.setInterpolator(new DecelerateInterpolator());
        rotation.start();
    }
    
    public static void rotateOnce(View view, float fromDegrees, float toDegrees, int duration) {
        if (view == null) return;
        ObjectAnimator rotation = ObjectAnimator.ofFloat(view, "rotation", fromDegrees, toDegrees);
        rotation.setDuration(duration);
        rotation.setInterpolator(new OvershootInterpolator(1.5f));
        rotation.start();
    }
    
    public static void pulse(View view) {
        if (view == null) return;
        view.animate()
            .scaleX(1.3f)
            .scaleY(1.3f)
            .setDuration(150)
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                view.animate()
                                    .scaleX(1.15f)
                                    .scaleY(1.15f)
                                    .setDuration(120)
                                    .withEndAction(new Runnable() {
                                        @Override
                                        public void run() {
                                            view.animate()
                                                .scaleX(1f)
                                                .scaleY(1f)
                                                .setDuration(120)
                                                .withEndAction(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        view.animate()
                                                            .scaleX(1.05f)
                                                            .scaleY(1.05f)
                                                            .setDuration(100)
                                                            .withEndAction(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    view.animate()
                                                                        .scaleX(1f)
                                                                        .scaleY(1f)
                                                                        .setDuration(100)
                                                                        .start();
                                                                }
                                                            })
                                                            .start();
                                                    }
                                                })
                                                .start();
                                        }
                                    })
                                    .start();
                            }
                        })
                        .start();
                }
            })
            .start();
    }
    
    public static void bounce(View view) {
        if (view == null) return;
        view.animate()
            .translationY(-30f)
            .setDuration(250)
            .setInterpolator(new DecelerateInterpolator())
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    view.animate()
                        .translationY(0f)
                        .setDuration(200)
                        .setInterpolator(BOUNCE)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                view.animate()
                                    .translationY(-10f)
                                    .setDuration(150)
                                    .withEndAction(new Runnable() {
                                        @Override
                                        public void run() {
                                            view.animate()
                                                .translationY(0f)
                                                .setDuration(150)
                                                .start();
                                        }
                                    })
                                    .start();
                            }
                        })
                        .start();
                }
            })
            .start();
    }
    
    public static void flash(View view) {
        if (view == null) return;
        view.animate()
            .alpha(0.3f)
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(50)
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    view.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(50)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                view.animate()
                                    .alpha(0.5f)
                                    .scaleX(1.05f)
                                    .scaleY(1.05f)
                                    .setDuration(50)
                                    .withEndAction(new Runnable() {
                                        @Override
                                        public void run() {
                                            view.animate()
                                                .alpha(1f)
                                                .scaleX(1f)
                                                .scaleY(1f)
                                                .setDuration(50)
                                                .withEndAction(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        view.animate()
                                                            .alpha(0.7f)
                                                            .setDuration(30)
                                                            .withEndAction(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    view.animate()
                                                                        .alpha(1f)
                                                                        .setDuration(30)
                                                                        .start();
                                                                }
                                                            })
                                                            .start();
                                                    }
                                                })
                                                .start();
                                        }
                                    })
                                    .start();
                            }
                        })
                        .start();
                }
            })
            .start();
    }
    
    public static void buttonPressEffect(View view) {
        if (view == null) return;
        view.animate()
            .scaleX(0.92f)
            .scaleY(0.92f)
            .alpha(0.8f)
            .setDuration(80)
            .start();
        
        view.animate()
            .rotation(-2f)
            .setDuration(80)
            .start();
    }
    
    public static void buttonReleaseEffect(View view) {
        if (view == null) return;
        view.animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .alpha(1f)
            .setDuration(100)
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .rotation(0f)
                        .setDuration(80)
                        .start();
                }
            })
            .start();
    }
    
    public static void successAnimation(View view) {
        if (view == null) return;
        view.animate()
            .scaleX(1.4f)
            .scaleY(1.4f)
            .rotation(10f)
            .setDuration(200)
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    view.animate()
                        .rotation(-5f)
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(150)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                view.animate()
                                    .rotation(0f)
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(200)
                                    .setInterpolator(new OvershootInterpolator(1.5f))
                                    .start();
                            }
                        })
                        .start();
                }
            })
            .start();
    }
    
    public static void pageTransition(View oldPage, View newPage, boolean forward, int duration) {
        if (oldPage == null || newPage == null) return;
        
        if (forward) {
            oldPage.animate()
                .translationX(-oldPage.getWidth() * 1.2f)
                .alpha(0f)
                .setDuration(duration)
                .setInterpolator(new AccelerateInterpolator(1.5f))
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        oldPage.setVisibility(View.GONE);
                        oldPage.setTranslationX(0);
                        oldPage.setAlpha(1f);
                    }
                })
                .start();
            
            newPage.setTranslationX(newPage.getWidth() * 1.2f);
            newPage.setAlpha(0f);
            newPage.setVisibility(View.VISIBLE);
            newPage.animate()
                .translationX(0)
                .alpha(1f)
                .setDuration(duration)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .start();
        } else {
            oldPage.animate()
                .translationX(oldPage.getWidth() * 1.2f)
                .alpha(0f)
                .setDuration(duration)
                .setInterpolator(new AccelerateInterpolator(1.5f))
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        oldPage.setVisibility(View.GONE);
                        oldPage.setTranslationX(0);
                        oldPage.setAlpha(1f);
                    }
                })
                .start();
            
            newPage.setTranslationX(-newPage.getWidth() * 1.2f);
            newPage.setAlpha(0f);
            newPage.setVisibility(View.VISIBLE);
            newPage.animate()
                .translationX(0)
                .alpha(1f)
                .setDuration(duration)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .start();
        }
    }
    
    public static void stopAllAnimations(View view) {
        if (view == null) return;
        view.animate().cancel();
        view.clearAnimation();
    }
    
    public static int getColor(String hexColor) {
        try {
            return Color.parseColor(hexColor);
        } catch (Exception e) {
            return Color.BLACK;
        }
    }
}